from pathlib import Path
C = Path('yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/source')
def put(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.strip() + '\n')
def edit(path, old, new):
    text = path.read_text()
    if old not in text: raise AssertionError(f'Missing patch anchor in {path}: {old[:100]}')
    path.write_text(text.replace(old, new))

p = C/'debezium/MySqlBinlogEngine.java'
edit(p, 'private final MySqlCdcSourceConfig config;', 'private final MySqlCdcSourceConfig config;\n    private final int serverId;')
edit(p, 'this.config = Objects.requireNonNull(config, "config");', '''this(config, config.serverId());
    }

    /** Uses a distinct replication connection while retaining the source partition and table contract. */
    public MySqlBinlogEngine(MySqlCdcSourceConfig config, int serverId) {
        if (serverId < 1) throw new IllegalArgumentException("Replication server ID must be positive");
        this.serverId = serverId;
        this.config = Objects.requireNonNull(config, "config");''')
edit(p, 'engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))', '''var properties = config.debeziumProperties(sessionId, historyPath);
        properties.setProperty("database.server.id", Integer.toString(serverId));
        engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))''')
edit(p, '.using(config.debeziumProperties(sessionId, historyPath))', '.using(properties)')
edit(p, '''<p>SQL Schema Evolution is unsupported in PR1. The history is essential for''', '''<p>SQL Schema Evolution is unsupported. The history is essential for''')

p = C/'MySqlCdcSourceConfig.java'
edit(p, '/?useCursorFetch=true&useServerPrepStmts=true', '/?useCursorFetch=true&useServerPrepStmts=true&connectTimeout=10000&socketTimeout=60000')
edit(p, 'public int serverId() {', '''/** Returns the reserved replication ID for one of at most sixteen snapshot readers. */
    public int snapshotServerId(int reader) {
        if (reader < 0 || reader >= 16 || serverId > Integer.MAX_VALUE - 16) {
            throw new IllegalArgumentException("Hybrid source requires a reserved range of seventeen replication IDs");
        }
        return serverId + reader + 1;
    }

    public int serverId() {''')
p = C/'MySqlHybridCdcSource.java'
edit(p, 'this.config = Objects.requireNonNull(config, "config");', 'this.config = Objects.requireNonNull(config, "config");\n        config.snapshotServerId(15);')
edit(p, ' + "-hybrid-" + chunkSize', ' + "-hybrid-backfill-v2-" + chunkSize')
start = p.read_text().index('/**')
end = p.read_text().index('public final class', start)
t = p.read_text(); p.write_text(t[:start] + '''/**
 * Reads parallel normalized snapshot chunks, then one filtered continuous Binlog stream.
 *
 * <p>Each chunk's Low-to-High changes are merged before output. The immutable result is
 * checkpointed before emission, preserving correction events across partial external writes.
 * A covering completed checkpoint gates the transition to continuous Binlog consumption.
 * JDBC output remains at-least-once; no XA or cross-table source transaction guarantee is implied.
 */
''' + t[end:])

put(C/'reader/MySqlSnapshotBackfillReader.java', '''
package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.debezium.MySqlBinlogEngine;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/** Fetcher-owned JDBC scan and bounded Binlog reconciliation for one chunk at a time. */
public final class MySqlSnapshotBackfillReader implements AutoCloseable {
    private static final int MAX_ROWS = 100_000;
    private static final long MAX_PAYLOAD_BYTES = 4L * 1024 * 1024;
    private static final long BINLOG_TIMEOUT_NANOS = Duration.ofSeconds(60).toNanos();
    private final MySqlCdcSourceConfig config;
    private final MySqlSnapshotSplitReader snapshots;
    private final MySqlBinlogEngine binlog;
    private final Callable<Void> afterSnapshot;
    private volatile Statement watermarkStatement;
    private volatile boolean cancelled;
    private boolean started;
    private Map<String, Object> partition;

    public MySqlSnapshotBackfillReader(MySqlCdcSourceConfig config, int reader) {
        this(config, reader, () -> null);
    }

    /** A package-local phase callback lets database tests place commits exactly inside the scan window. */
    MySqlSnapshotBackfillReader(MySqlCdcSourceConfig config, int reader, Callable<Void> afterSnapshot) {
        this.config = Objects.requireNonNull(config, "config");
        this.afterSnapshot = Objects.requireNonNull(afterSnapshot, "afterSnapshot");
        snapshots = new MySqlSnapshotSplitReader(config);
        binlog = new MySqlBinlogEngine(config, config.snapshotServerId(reader));
    }

    /**
     * Produces a complete normalized result without publishing any rows to the downstream.
     *
     * <p>Equal SQL watermarks prove an empty interval. Otherwise an ordered Debezium event
     * or heartbeat must reach High; neither an empty queue nor a timeout implies completion.
     */
    public MySqlSnapshotSplit read(MySqlSnapshotSplit split) throws Exception {
        checkCancelled();
        if (split.prepared() || split.lastEmittedKey() != null) {
            throw new IllegalArgumentException("Backfill requires an unprepared complete chunk range");
        }
        ensureBinlog();
        BinlogOffset low = watermark();
        MySqlChunkBackfill buffer = new MySqlChunkBackfill(split,
                Objects.requireNonNull(config.schemas().get(split.tableId()), "chunk schema"),
                MAX_ROWS, MAX_PAYLOAD_BYTES);
        try {
            snapshots.open(split);
            boolean complete = false;
            while (!complete) {
                checkCancelled();
                var batch = snapshots.fetch();
                while (batch.nextSplit() != null) {
                    MySqlHybridFetchedRecord record;
                    while ((record = batch.nextRecordFromSplit()) != null) {
                        for (var row : record.rows()) buffer.addSnapshot(row);
                    }
                }
                complete = batch.finishedSplits().contains(split.splitId());
                batch.recycle();
            }
            afterSnapshot.call();
            BinlogOffset high = watermark();
            if (BinlogOffsetOrder.compare(low, high) > 0) {
                throw new IllegalStateException("MySQL snapshot watermarks moved backwards");
            }
            if (BinlogOffsetOrder.compare(low, high) != 0) {
                long deadline = System.nanoTime() + BINLOG_TIMEOUT_NANOS;
                while (true) {
                    checkDeadline(deadline);
                    BinlogEvent event = binlog.poll();
                    if (event == null) continue;
                    int highOrder = BinlogOffsetOrder.compare(event.offset(), high);
                    if (BinlogOffsetOrder.compare(event.offset(), low) > 0 && highOrder <= 0
                            && !event.records().isEmpty()
                            && split.tableId().equals(event.records().getFirst().tableId())) {
                        buffer.apply(event);
                    }
                    if (highOrder >= 0) break;
                }
            }
            MySqlSnapshotSplit prepared = split.withOutput(new MySqlSnapshotOutput(low, high, buffer.finish()));
            // Fail before any downstream output if the exact persisted representation cannot fit.
            new MySqlHybridSplitSerializer().serialize(prepared);
            return prepared;
        } finally {
            snapshots.close();
        }
    }

    private void ensureBinlog() throws Exception {
        if (partition != null) return;
        if (!started) {
            started = true;
            binlog.start(new MySqlBinlogSplit("snapshot-backfill", null, new byte[0]));
        }
        long deadline = System.nanoTime() + BINLOG_TIMEOUT_NANOS;
        while (partition == null) {
            checkDeadline(deadline);
            BinlogEvent event = binlog.poll();
            if (event != null && binlog.snapshotHistory().length > 0) partition = event.offset().partition();
        }
    }

    private BinlogOffset watermark() throws SQLException {
        checkCancelled();
        try (Connection connection = config.openSnapshotConnection(); Statement statement = connection.createStatement()) {
            watermarkStatement = statement;
            statement.setQueryTimeout(60);
            checkCancelled();
            ResultSet rows;
            try {
                rows = statement.executeQuery("SHOW BINARY LOG STATUS");
            } catch (SQLException unsupported) {
                checkCancelled();
                try { rows = statement.executeQuery("SHOW MASTER STATUS"); }
                catch (SQLException failure) { failure.addSuppressed(unsupported); throw failure; }
            }
            try (ResultSet status = rows) {
                if (!status.next()) throw new SQLException("MySQL binary logging is disabled");
                String file = status.getString(1);
                long position = status.getLong(2);
                if (file == null || file.isBlank() || position < 1) throw new SQLException("Invalid MySQL Binlog boundary");
                return new BinlogOffset(partition, Map.of("file", file, "pos", position));
            }
        } finally {
            watermarkStatement = null;
        }
    }

    private void checkDeadline(long deadline) throws SQLException {
        checkCancelled();
        if (System.nanoTime() - deadline >= 0) {
            throw new SQLException("MySQL Binlog did not reach the required snapshot boundary before timeout");
        }
    }

    private void checkCancelled() throws SQLException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new SQLException("MySQL chunk backfill was cancelled");
    }

    public void cancel() {
        cancelled = true;
        snapshots.cancel();
        binlog.requestStop();
        Statement running = watermarkStatement;
        if (running != null) Thread.ofVirtual().name("yak-mysql-watermark-cancel").start(() -> {
            try { running.cancel(); }
            catch (SQLException ignored) { /* Owning fetcher observes termination and releases resources. */ }
        });
    }

    @Override public void close() throws Exception {
        cancel();
        Exception failure = null;
        try { snapshots.close(); } catch (Exception error) { failure = error; }
        try { binlog.close(); } catch (Exception error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
}
''')

put(C/'reader/MySqlHybridSplitReader.java', '''
package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.debezium.MySqlBinlogEngine;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
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
public final class MySqlHybridSplitReader implements SplitReader<MySqlHybridFetchedRecord, MySqlHybridSplit> {
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
    private MySqlBinlogSplitFilter filter;
    private MySqlSnapshotSplit active;
    private int outputPosition;
    private boolean streaming;
    private boolean closed;
    private BinlogEvent bootstrapCandidate;

    public MySqlHybridSplitReader(MySqlCdcSourceConfig config) { this(config, new TableMetrics(), 0); }
    public MySqlHybridSplitReader(MySqlCdcSourceConfig config, TableMetrics metrics) { this(config, metrics, 0); }
    public MySqlHybridSplitReader(MySqlCdcSourceConfig config, TableMetrics metrics, int reader) {
        this.config = Objects.requireNonNull(config, "config");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        generation = metrics.generation();
        tables = Set.copyOf(config.schemas().keySet());
        snapshots = new MySqlSnapshotBackfillReader(config, reader);
    }

    @Override public void addSplits(List<MySqlHybridSplit> splits) throws Exception {
        for (MySqlHybridSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                pending.addLast(snapshot);
                if (snapshot.prepared()) released.add(snapshot.splitId());
            } else if (split instanceof MySqlHybridBinlogSplit stream) {
                if (binlogSplit != null) throw new IllegalStateException("Hybrid Reader already owns Binlog");
                binlogSplit = stream;
                history = stream.binlog().schemaHistory();
                if (stream.phase() == MySqlHybridBinlogSplit.Phase.BOOTSTRAP) startBinlog(stream);
                else if (stream.phase() == MySqlHybridBinlogSplit.Phase.STREAMING) startStreaming(stream);
            } else throw new IllegalArgumentException("Unknown MySQL Hybrid split");
        }
    }

    @Override public RecordsWithSplitIds<MySqlHybridFetchedRecord> fetch() throws Exception {
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

    private RecordsWithSplitIds<MySqlHybridFetchedRecord> fetchRecords() throws Exception {
        if (closed || cancelled) throw new IllegalStateException("MySQL hybrid reader is closed or cancelled");
        if (binlogSplit != null && binlogSplit.phase() == MySqlHybridBinlogSplit.Phase.BOOTSTRAP) {
            if (bootstrapCandidate == null) bootstrapCandidate = binlog.poll();
            if (bootstrapCandidate == null) return empty();
            byte[] discovered = binlog.snapshotHistory();
            if (discovered.length == 0) { Thread.sleep(20); return empty(); }
            BinlogEvent first = bootstrapCandidate;
            history = discovered;
            binlog.close();
            binlog = null;
            binlogSplit = binlogSplit.withAnchor(first.offset(), history);
            bootstrapCandidate = null;
            return records(MySqlHybridBinlogSplit.ID,
                    new MySqlHybridFetchedRecord(List.of(), null, first.offset(), true), false);
        }
        if (active == null && !pending.isEmpty()) {
            active = pending.removeFirst();
            outputPosition = active.outputPosition();
            if (!active.prepared()) {
                active = snapshots.read(active);
                return records(active.splitId(), MySqlHybridFetchedRecord.prepared(active), false);
            }
        }
        if (active != null) {
            String id = active.splitId();
            if (active.finished() != null) {
                active = null;
                released.remove(id);
                return new RecordsBySplits<>(Map.of(), Set.of(id));
            }
            if (!released.contains(id)) { Thread.sleep(20); return empty(); }
            List<io.yak.ops.core.data.TableRecord> rows = active.output().rows();
            int next = Math.min(rows.size(), outputPosition + OUTPUT_BATCH_SIZE);
            MySqlHybridFetchedRecord batch = MySqlHybridFetchedRecord.snapshotRows(rows.subList(outputPosition, next), next);
            outputPosition = next;
            boolean complete = next == rows.size();
            if (complete) { active = null; released.remove(id); }
            return records(id, batch, complete);
        }
        if (binlogSplit != null && resume != null && !streaming) {
            startStreaming(binlogSplit.startStreaming(resume.highWatermark(), resume.finishedSplits()));
        }
        if (streaming) {
            BinlogEvent event = binlog.poll();
            if (event != null) {
                BinlogEvent filtered = filter.filter(event);
                return records(MySqlHybridBinlogSplit.ID,
                        new MySqlHybridFetchedRecord(filtered.records(), null, filtered.offset(), false), false);
            }
        } else Thread.sleep(20);
        return empty();
    }

    private void startBinlog(MySqlHybridBinlogSplit split) throws Exception {
        binlog = new MySqlBinlogEngine(config);
        binlog.start(split.binlog());
    }

    private void startStreaming(MySqlHybridBinlogSplit split) throws Exception {
        filter = new MySqlBinlogSplitFilter(config.schemas(), split.finishedSplits());
        if (binlog != null) throw new IllegalStateException("Bootstrap Binlog was not closed before handoff");
        // Snapshot backfill connections are no longer needed once this Reader enters streaming.
        snapshots.close();
        binlogSplit = split;
        history = split.binlog().schemaHistory();
        startBinlog(split);
        streaming = true;
    }

    public void releaseSnapshot(String splitId) { if (!cancelled) released.add(splitId); }
    public void resumeBinlog(MySqlResumeBinlogEvent event) { resume = Objects.requireNonNull(event, "event"); }
    public byte[] snapshotHistory() { return history.clone(); }
    @Override public void wakeUp() { /* New assignments must not cancel active JDBC or Binlog I/O. */ }
    @Override public void cancel() {
        cancelled = true;
        snapshots.cancel();
        MySqlBinlogEngine running = binlog;
        if (running != null) running.requestStop();
    }
    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        cancel();
        Exception failure = null;
        try { snapshots.close(); } catch (Exception error) { failure = error; }
        try { if (binlog != null) binlog.close(); } catch (Exception error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        }
        pending.clear();
        released.clear();
        active = null;
        if (failure != null) throw failure;
    }
    private static RecordsWithSplitIds<MySqlHybridFetchedRecord> empty() {
        return new RecordsBySplits<>(Map.of(), Set.of());
    }
    private static RecordsWithSplitIds<MySqlHybridFetchedRecord> records(
            String id, MySqlHybridFetchedRecord record, boolean complete) {
        return new RecordsBySplits<>(Map.of(id, List.of(record)), complete ? Set.of(id) : Set.of());
    }
}
''')

# New instances reserve aligned, non-overlapping blocks; the existing atomic claim fences block collisions.
p = Path('yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/main/java/io/yak/ops/plugin/task/datasync/plan/MySqlCdcReplicationIdentity.java')
edit(p, 'Math.floorMod(instanceId.hashCode(), 2_000_000_000) + 1', 'Math.floorMod(instanceId.hashCode(), 62_500_000) * 32 + 1')
edit(p, 'Returns the stable positive replication server ID for one instance.', 'Returns the first ID of an aligned block reserved for one Binlog and sixteen snapshot readers.')
edit(p, 'Declares the replication server-ID resource for atomic admission.', 'Declares the aligned replication block resource for atomic admission; all platform instances use this allocator.')

# Equal GTID sets do not imply equal positions within the same binlog history.
p = C/'offset/BinlogOffsetOrder.java'
edit(p, '''            return compareEventRow(left.position(), right.position());
        }

        String leftFile''', '''            if (text(left.position(), "file") == null || text(right.position(), "file") == null) {
                return compareEventRow(left.position(), right.position());
            }
        }

        String leftFile''')
