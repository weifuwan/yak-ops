package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.cdc.mysql.debezium.MySqlBinlogEngine;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.core.data.TableId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches normalized chunks and a filtered Binlog stream without advancing mailbox checkpoint state.
 *
 * <p>A prepared result waits for a completed checkpoint before any output batch is released.
 * Restored results are replayed verbatim, not recomputed from a later database snapshot.
 */
public final class MySqlSplitReader implements SplitReader<MySqlFetchedRecord, MySqlSplit> {
    private static final int OUTPUT_BATCH_SIZE = 256;
    private final MySqlCdcSourceConfig config;
    private final MySqlSnapshotBackfillReader snapshots;
    private final TableMetrics metrics;
    private final long generation;
    private final Set<TableId> tables;
    private final Deque<MySqlSnapshotSplit> pending = new ArrayDeque<>();
    private final Set<String> released = ConcurrentHashMap.newKeySet();
    private volatile MySqlBinlogEngine binlog;
    private volatile byte[] history = new byte[0];
    private volatile MySqlResumeBinlogEvent resume;
    private volatile boolean cancelled;
    private MySqlHybridBinlogSplit binlogSplit;
    private MySqlBinlogSplit standalone;
    private final String fingerprint;
    private MySqlBinlogSplitFilter filter;
    private MySqlSnapshotSplit active;
    private int outputPosition;
    // Written by the fetcher and inspected by the source mailbox during checkpoints.
    private volatile boolean streaming;
    private volatile boolean snapshotsFinished;
    private boolean snapshotsClosed;
    private boolean closed;
    private BinlogEvent bootstrapCandidate;

    public MySqlSplitReader(MySqlCdcSourceConfig config) {
        this(config, new TableMetrics(), 0, null);
    }

    public MySqlSplitReader(MySqlCdcSourceConfig config, TableMetrics metrics) {
        this(config, metrics, 0, null);
    }

    public MySqlSplitReader(MySqlCdcSourceConfig config, TableMetrics metrics, int reader) {
        this(config, metrics, reader, null);
    }

    public MySqlSplitReader(MySqlCdcSourceConfig config, TableMetrics metrics, int reader, String fingerprint) {
        this.fingerprint = fingerprint;
        this.config = Objects.requireNonNull(config, "config");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        generation = metrics.generation();
        tables = Set.copyOf(config.schemas().keySet());
        snapshots = new MySqlSnapshotBackfillReader(config, reader);
    }

    @Override
    public void addSplits(List<MySqlSplit> splits) throws Exception {
        for (MySqlSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                // Typed backfill freezes every range before emitting rows; checkpointed
                // output positions preserve both keyed updates and keyless duplicates.
                pending.addLast(snapshot);
                if (snapshot.prepared()) released.add(snapshot.splitId());
            } else if (split instanceof MySqlHybridBinlogSplit stream) {
                if (binlogSplit != null) throw new IllegalStateException("Hybrid Reader already owns Binlog");
                binlogSplit = stream;
                history = stream.binlog().schemaHistory();
                if (stream.phase() == MySqlHybridBinlogSplit.Phase.BOOTSTRAP) startBinlog(stream);
                else if (stream.phase() == MySqlHybridBinlogSplit.Phase.STREAMING) startStreaming(stream);
            } else if (split instanceof MySqlBinlogSplit stream) {
                if (binlogSplit != null || standalone != null) {
                    throw new IllegalStateException("MySQL Binlog Reader already has a stream");
                }
                if (fingerprint == null || !fingerprint.equals(stream.definitionFingerprint())) {
                    throw new IllegalArgumentException("MySQL Binlog split belongs to another source");
                }
                standalone = stream;
                binlog = new MySqlBinlogEngine(config);
                binlog.start(stream);
                streaming = true;
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
        if (binlogSplit != null && binlogSplit.phase() == MySqlHybridBinlogSplit.Phase.BOOTSTRAP) {
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
            return records(
                    MySqlHybridBinlogSplit.ID, new MySqlFetchedRecord(List.of(), null, first.offset(), true), false);
        }
        if (active == null && !pending.isEmpty()) {
            active = pending.removeFirst();
            outputPosition = active.outputPosition();
            if (!active.prepared()) {
                active = snapshots.read(active);
                return records(active.splitId(), MySqlFetchedRecord.prepared(active), false);
            }
        }
        if (active != null) {
            String id = active.splitId();
            if (active.finished() != null) {
                active = null;
                released.remove(id);
                return new RecordsBySplits<>(Map.of(), Set.of(id));
            }
            if (!released.contains(id)) {
                Thread.sleep(20);
                return empty();
            }
            List<io.yak.ops.core.data.TableRecord> rows = active.output().rows();
            int next = Math.min(rows.size(), outputPosition + OUTPUT_BATCH_SIZE);
            MySqlFetchedRecord batch = MySqlFetchedRecord.snapshotRows(rows.subList(outputPosition, next), next);
            outputPosition = next;
            boolean complete = next == rows.size();
            if (complete) {
                active = null;
                released.remove(id);
            }
            return records(id, batch, complete);
        }
        if (snapshotsFinished && !snapshotsClosed) {
            snapshots.close();
            snapshotsClosed = true;
        }
        if (binlogSplit != null && resume != null && !streaming) {
            startStreaming(binlogSplit.startStreaming(resume.highWatermark(), resume.finishedSplits()));
        }
        if (streaming) {
            BinlogEvent event = binlog.poll();
            if (event != null) {
                BinlogEvent filtered = filter == null ? event : filter.filter(event);
                return records(MySqlHybridBinlogSplit.ID, MySqlFetchedRecord.binlog(filtered), false);
            }
        } else Thread.sleep(20);
        return empty();
    }

    private void startBinlog(MySqlHybridBinlogSplit split) throws Exception {
        binlog = new MySqlBinlogEngine(config);
        binlog.start(split.binlog());
    }

    private void startStreaming(MySqlHybridBinlogSplit split) throws Exception {
        filter = MySqlBinlogSplitFilter.fromFinishedSplits(config.schemas(), split.finishedSplits());
        if (binlog != null) throw new IllegalStateException("Bootstrap Binlog was not closed before handoff");
        // Snapshot backfill connections are no longer needed once this Reader enters streaming.
        snapshots.close();
        binlogSplit = split;
        history = split.binlog().schemaHistory();
        startBinlog(split);
        streaming = true;
    }

    public void finishSnapshots() {
        snapshotsFinished = true;
    }

    public void releaseSnapshot(String splitId) {
        if (!cancelled) released.add(splitId);
    }

    public void resumeBinlog(MySqlResumeBinlogEvent event) {
        resume = Objects.requireNonNull(event, "event");
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
            MySqlBinlogEngine active = binlog;
            if (active == null) {
                throw new IllegalStateException("Active MySQL Binlog reader is missing during checkpoint");
            }
            try {
                byte[] current = active.snapshotHistory();
                if (current.length == 0 && standalone == null) {
                    throw new IllegalStateException("Active MySQL Binlog has no schema history");
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
        MySqlBinlogEngine running = binlog;
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
        released.clear();
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
