package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumSchemaHistorySnapshot;
import io.yak.ops.connector.cdc.mysql.debezium.reader.BinlogSplitReader;
import io.yak.ops.connector.cdc.mysql.debezium.task.MySqlBinlogSplitReadTask;
import io.yak.ops.connector.cdc.mysql.debezium.task.MySqlSnapshotSplitReadTask;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetUtils;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fetches normalized chunks and a filtered Binlog stream without advancing mailbox checkpoint state.
 *
 * <p>Normalized snapshot chunks are emitted in one reader mailbox step. Incomplete chunks
 * are retried from their stable range after a failure, without checkpointing source row images.
 */
public final class MySqlSplitReader implements SplitReader<MySqlFetchedRecord, MySqlSplit> {
    private final MySqlSourceConfig config;
    private final MySqlSourceReaderContext readerContext;
    private MySqlSnapshotSplitReadTask snapshots;
    private final int readerIndex;
    private final TableMetrics metrics;
    private final long generation;
    private volatile Set<TableId> tables;
    private final Deque<MySqlSnapshotSplit> pending = new ArrayDeque<>();
    private volatile MySqlBinlogSplitReadTask binlog;
    private volatile byte[] history = new byte[0];
    private volatile DebeziumOffset resumeHigh;
    private volatile List<FinishedSnapshotSplitInfo> resumeFinishedSplits;
    private volatile boolean cancelled;
    private MySqlBinlogSplit binlogSplit;
    private BinlogSplitReader filter;
    private MySqlSnapshotSplit active;
    // Written by the fetcher and inspected by the source mailbox during checkpoints.
    private volatile boolean streaming;
    private volatile boolean snapshotsFinished;
    private boolean snapshotsClosed;
    private boolean binlogPauseReported;
    private boolean closed;
    private BinlogEvent bootstrapCandidate;
    private boolean streamOnlyBootstrap;
    private volatile boolean snapshotOnlyStop;

    public MySqlSplitReader(MySqlSourceConfig config) {
        this(config, new TableMetrics(), 0);
    }

    public MySqlSplitReader(MySqlSourceConfig config, TableMetrics metrics) {
        this(config, metrics, 0);
    }

    public MySqlSplitReader(MySqlSourceConfig config, TableMetrics metrics, int reader) {
        this(config, metrics, reader, null);
    }

    MySqlSplitReader(
            MySqlSourceConfig config, TableMetrics metrics, int reader, MySqlSourceReaderContext readerContext) {
        this.config = Objects.requireNonNull(config, "config");
        this.readerContext = readerContext;
        this.readerIndex = reader;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        generation = metrics.generation();
        tables = Set.copyOf(config.schemas().keySet());
        snapshots = new MySqlSnapshotSplitReadTask(config, reader);
    }

    TableSchema snapshotSchema(TableId tableId) {
        return Objects.requireNonNull(config.schemas().get(tableId), "snapshot table schema");
    }

    io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions tableOptions(TableId tableId) {
        return config.tableOptions(tableId);
    }

    void registerDiscoveredTable(TableId table, io.yak.ops.core.schema.Schema schema) {
        config.registerDiscoveredTable(table, schema);
        tables = Set.copyOf(config.schemas().keySet());
        metrics.register(table);
    }

    @Override
    public void addSplits(List<MySqlSplit> splits) throws Exception {
        for (MySqlSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                if (snapshotsClosed) {
                    // The initial handoff closes JDBC snapshot resources. A later table scan
                    // must open a new task, including when Binlog and Snapshot share reader 0.
                    snapshots = new MySqlSnapshotSplitReadTask(config, readerIndex);
                    snapshotsClosed = false;
                    snapshotsFinished = false;
                }
                pending.addLast(snapshot);
            } else if (split instanceof MySqlBinlogSplit stream) {
                if (binlogSplit != null
                        && !(binlogSplit.phase() == MySqlBinlogSplit.Phase.PAUSED
                                && stream.phase() == MySqlBinlogSplit.Phase.STREAMING
                                && !streaming
                                && binlog == null)) {
                    throw new IllegalStateException("MySQL Reader already owns a Binlog split");
                }
                binlogSplit = stream;
                binlogPauseReported = false;
                if (readerContext != null) {
                    readerContext.setHasAssignedBinlogSplit(true);
                    if (stream.isSuspended() && stream.phase() == MySqlBinlogSplit.Phase.PAUSED) {
                        readerContext.suspendBinlogSplitReader();
                    } else {
                        readerContext.wakeupSuspendedBinlogSplitReader();
                    }
                }
                history = stream.schemaHistory();
                if (stream.phase() == MySqlBinlogSplit.Phase.BOOTSTRAP) {
                    startBinlog(stream);
                } else if (stream.phase() == MySqlBinlogSplit.Phase.STREAMING) {
                    startStreaming(stream);
                } else if (stream.phase() == MySqlBinlogSplit.Phase.STANDALONE) {
                    binlogSplit = stream;
                    // A missing offset is an unresolved initial startup, not a checkpointed
                    // position. Bootstrap the schema at a real MySQL position first, then
                    // restart the engine from the selected offset and keep all prefetch
                    // outside the mailbox-owned split state.
                    streamOnlyBootstrap = stream.offset() == null;
                    binlog = new MySqlBinlogSplitReadTask(config);
                    binlog.start(streamOnlyBootstrap ? MySqlBinlogSplit.forInitialSnapshot() : stream);
                    streaming = !streamOnlyBootstrap;
                }
            } else throw new IllegalArgumentException("Unknown MySQL split");
        }
    }

    @Override
    public RecordsWithSplitIds<MySqlFetchedRecord> fetch() throws Exception {
        try {
            var result = fetchRecords();
            metrics.observed(generation);
            return result;
        } catch (Exception failure) {
            if (!cancelled && !(failure instanceof InterruptedException)) {
                if (active != null) metrics.readFailed(generation, active.tableId(), false);
                else tables.forEach(table -> metrics.readFailed(generation, table, streaming));
            }
            throw failure;
        }
    }

    private RecordsWithSplitIds<MySqlFetchedRecord> fetchRecords() throws Exception {
        if (closed || cancelled) throw new IllegalStateException("MySQL hybrid reader is closed or cancelled");
        if (snapshotOnlyStop) {
            snapshotOnlyStop = false;
            if (binlog != null) {
                binlog.requestStop();
                binlog.close();
                binlog = null;
            }
            binlogSplit = null;
            snapshots.close();
            snapshotsClosed = true;
            streaming = false;
            return new RecordsBySplits<>(Map.of(), Set.of(MySqlBinlogSplit.ID));
        }
        if (streamOnlyBootstrap) {
            if (bootstrapCandidate == null) bootstrapCandidate = binlog.poll();
            if (bootstrapCandidate == null) return empty();
            byte[] discovered = binlog.snapshotHistory();
            if (discovered.length == 0) return empty();
            DebeziumOffset requested = resolveStreamOnlyStart(bootstrapCandidate.offset());
            // Debezium 2.7 filters Schema History by the requested Binlog offset.
            // A schema-only bootstrap from NOW otherwise yields an empty recovered
            // schema for an older specific/timestamp/earliest position.
            if (requested.compareTo(bootstrapCandidate.offset()) < 0) {
                discovered = DebeziumSchemaHistorySnapshot.anchorBootstrap(discovered, requested);
            }
            history = discovered;
            binlog.close();
            binlog = new MySqlBinlogSplitReadTask(config);
            binlogSplit = new MySqlBinlogSplit(requested, discovered);
            binlog.start(binlogSplit);
            // Complete the bootstrap in one mailbox step; checkpoint remains unstarted
            // until an event from the requested position is actually emitted.
            streamOnlyBootstrap = false;
            streaming = true;
            bootstrapCandidate = null;
            return empty();
        }
        if (binlogSplit != null && binlogSplit.phase() == MySqlBinlogSplit.Phase.BOOTSTRAP) {
            if (bootstrapCandidate == null) bootstrapCandidate = binlog.poll();
            if (bootstrapCandidate == null) return empty();
            byte[] discovered = binlog.snapshotHistory();
            if (discovered.length == 0) {
                Thread.sleep(20);
                return empty();
            }
            BinlogEvent first = bootstrapCandidate;
            history = discovered;
            binlog.close();
            binlog = null;
            binlogSplit = binlogSplit.withAnchor(first.offset(), history);
            bootstrapCandidate = null;
            return records(MySqlBinlogSplit.ID, MySqlFetchedRecord.lowWatermark(first.offset()), false);
        }
        if (readerContext != null
                && readerContext.isBinlogSplitReaderSuspended()
                && binlogSplit != null
                && !binlogPauseReported) {
            if (streaming) {
                MySqlBinlogSplitReadTask running = binlog;
                try {
                    if (running != null) {
                        byte[] currentHistory = running.snapshotHistory();
                        if (currentHistory.length > 0) history = currentHistory;
                        running.requestStop();
                        running.close();
                    }
                } finally {
                    binlog = null;
                    filter = null;
                    streaming = false;
                }
            }
            binlogPauseReported = true;
            return new RecordsBySplits<>(Map.of(), Set.of(MySqlBinlogSplit.ID));
        }
        if (active == null && !pending.isEmpty()) {
            active = pending.removeFirst();
            if (!active.prepared()) {
                active = snapshots.read(active);
            }
        }
        if (active != null) {
            MySqlSnapshotSplit chunk = active;
            active = null;
            if (chunk.finished() != null) {
                return new RecordsBySplits<>(Map.of(), Set.of(chunk.splitId()));
            }
            if (chunk.output() == null) {
                throw new IllegalStateException("Snapshot chunk has no normalized output");
            }
            // A chunk is emitted atomically in one mailbox operation. If interrupted,
            // restoration starts from its typed range and repeats Low/High/Backfill.
            return records(chunk.splitId(), MySqlFetchedRecord.prepared(chunk), true);
        }
        if (snapshotsFinished && !snapshotsClosed) {
            snapshots.close();
            snapshotsClosed = true;
        }
        if (binlogSplit != null && resumeHigh != null && !streaming) {
            startStreaming(binlogSplit.startStreaming(resumeHigh, resumeFinishedSplits));
        }
        if (streaming) {
            BinlogEvent event = filter == null ? binlog.poll() : filter.poll(binlog);
            if (event != null) {
                // Flink CDC's TIMESTAMP mode starts scanning a containing Binlog file.
                // Filter the earlier events within that file without converting a
                // fetched offset into delivered progress ahead of the mailbox.
                StartupOptions startup = config.getStartupOptions();
                if (startup.startupMode() == StartupMode.TIMESTAMP
                        && binlogSplit != null
                        && binlogSplit.phase() == MySqlBinlogSplit.Phase.STANDALONE) {
                    Object value = event.offset().position().get("ts_sec");
                    if (!(value instanceof Number timestamp)) {
                        if (!event.records().isEmpty()) {
                            throw new IllegalStateException("Timestamp startup requires Debezium event timestamp");
                        }
                    } else if (timestamp.longValue() < startup.binlogOffset.getTimestampSec()) {
                        return records(
                                MySqlBinlogSplit.ID,
                                MySqlFetchedRecord.binlog(new BinlogEvent(List.of(), event.offset())),
                                false);
                    }
                }
                return records(MySqlBinlogSplit.ID, MySqlFetchedRecord.binlog(event), false);
            }
        } else Thread.sleep(20);
        return empty();
    }

    private void startBinlog(MySqlBinlogSplit split) throws Exception {
        binlog = new MySqlBinlogSplitReadTask(config);
        binlog.start(split);
    }

    private void startStreaming(MySqlBinlogSplit split) throws Exception {
        filter = BinlogSplitReader.fromFinishedSplits(config.schemas(), split.finishedSplits());
        if (binlog != null) throw new IllegalStateException("Bootstrap Binlog was not closed before handoff");
        // Snapshot backfill connections are no longer needed once this Reader enters streaming.
        snapshots.close();
        snapshotsClosed = true;
        binlogSplit = split;
        history = split.schemaHistory();
        resumeHigh = null;
        resumeFinishedSplits = null;
        startBinlog(split);
        streaming = true;
        binlogPauseReported = false;
        if (readerContext != null) readerContext.wakeupSuspendedBinlogSplitReader();
    }

    /** Stores a paused Binlog split after the fetcher has stopped its live Debezium task. */
    public void suspendBinlog(MySqlBinlogSplit split) {
        Objects.requireNonNull(split, "split");
        if (split.phase() != MySqlBinlogSplit.Phase.PAUSED) {
            throw new IllegalArgumentException("A suspended Binlog split must be in PAUSED phase");
        }
        if (binlog != null || streaming) {
            throw new IllegalStateException("Binlog fetcher must stop before its split is checkpointed as paused");
        }
        binlogSplit = split;
        history = split.schemaHistory();
        filter = null;
        resumeHigh = null;
        resumeFinishedSplits = null;
        binlogPauseReported = true;
    }

    /**
     * Stops the snapshot-only bootstrap split through the fetcher completion marker.
     * SourceReaderBase removes its mailbox state only after consuming that marker.
     */
    public void stopSnapshotOnly() {
        snapshotsFinished = true;
        snapshotOnlyStop = true;
        if (readerContext != null) readerContext.wakeupSuspendedBinlogSplitReader();
    }

    private DebeziumOffset resolveStreamOnlyStart(DebeziumOffset anchor) throws Exception {
        StartupOptions options = config.getStartupOptions();
        if (options.startupMode() == StartupMode.LATEST_OFFSET) return anchor;

        BinlogOffset requested = options.binlogOffset;
        BinlogOffset resolved = BinlogOffsetUtils.initializeEffectiveOffset(requested, config);
        String file = resolved.getFilename();
        long position = resolved.getPosition();
        if (options.startupMode() == StartupMode.EARLIEST_OFFSET || (file == null || file.isBlank())) {
            try (Connection connection = config.openSnapshotConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SHOW BINARY LOGS")) {
                if (!rows.next()) throw new IllegalStateException("MySQL has no retained binary logs");
                file = rows.getString(1);
                position = 4L;
            }
        }
        if (file == null || file.isBlank() || position < 0) {
            throw new IllegalArgumentException("Invalid MySQL Binlog startup file and position");
        }
        Map<String, Object> offset = new LinkedHashMap<>(anchor.position());
        offset.remove("gtids");
        offset.remove("ts_sec");
        offset.put("file", file);
        offset.put("pos", position);
        offset.put("event", requested.getRestartSkipEvents());
        offset.put("row", requested.getRestartSkipRows());
        offset.put("snapshot", false);
        if (requested.getGtidSet() != null) {
            offset.put("gtids", requested.getGtidSet());
        }
        return new DebeziumOffset(anchor.partition(), offset);
    }

    public void finishSnapshots() {
        snapshotsFinished = true;
    }

    public void resumeBinlog(DebeziumOffset high, List<FinishedSnapshotSplitInfo> finished) {
        resumeHigh = Objects.requireNonNull(high, "high");
        resumeFinishedSplits = List.copyOf(Objects.requireNonNull(finished, "finished"));
        if (resumeFinishedSplits.isEmpty()) throw new IllegalArgumentException("Missing Binlog metadata");
        if (readerContext != null) readerContext.wakeupSuspendedBinlogSplitReader();
    }

    /**
     * Checkpoint the active Debezium schema history, not the initial snapshot anchor.
     *
     * <p>Debezium records every DDL in its history file before handing over subsequent
     * rows. The bootstrap history is only valid until streaming begins. Retaining it
     * with an advanced Binlog offset would restore an obsolete table shape.
     *
     * <p>The history file may contain later DDL from Debezium prefetch: Debezium's
     * history recovery selects records applicable to the restored Binlog offset.
     */
    public byte[] snapshotHistory() {
        if (streaming) {
            MySqlBinlogSplitReadTask active = binlog;
            if (active == null) {
                throw new IllegalStateException("Active MySQL Binlog reader is missing during checkpoint");
            }
            try {
                byte[] current = active.snapshotHistory();
                if (current.length == 0) {
                    throw new IllegalStateException("MySQL Binlog has no complete offset/schema-history checkpoint");
                }
                history = current;
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot checkpoint live MySQL schema history", failure);
            }
        }
        return history.clone();
    }

    @Override
    public void wakeUp() {
        /* New assignments must not cancel active JDBC or Binlog I/O. */
    }

    @Override
    public void cancel() {
        cancelled = true;
        snapshots.cancel();
        MySqlBinlogSplitReadTask running = binlog;
        if (running != null) running.requestStop();
    }

    @Override
    public void close() throws Exception {
        if (closed) return;
        closed = true;
        cancel();
        Exception failure = null;
        try {
            snapshots.close();
        } catch (Exception error) {
            failure = error;
        }
        try {
            if (binlog != null) binlog.close();
        } catch (Exception error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        pending.clear();
        active = null;
        if (failure != null) throw failure;
    }

    private static RecordsWithSplitIds<MySqlFetchedRecord> empty() {
        return new RecordsBySplits<>(Map.of(), Set.of());
    }

    private static RecordsWithSplitIds<MySqlFetchedRecord> records(
            String id, MySqlFetchedRecord record, boolean complete) {
        return new RecordsBySplits<>(Map.of(id, List.of(record)), complete ? Set.of(id) : Set.of());
    }
}
