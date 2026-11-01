package io.yak.ops.connector.cdc.mysql.debezium.task;

import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumUtils;
import io.yak.ops.connector.cdc.mysql.debezium.reader.SnapshotChunkBackfill;
import io.yak.ops.connector.cdc.mysql.debezium.reader.SnapshotSplitReader;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/** Fetcher-owned JDBC scan and bounded Binlog reconciliation for one chunk at a time. */
public final class MySqlSnapshotSplitReadTask implements AutoCloseable {
    private static final int MAX_ROWS = 100_000;
    private static final long BINLOG_TIMEOUT_NANOS = Duration.ofSeconds(60).toNanos();
    private final MySqlSourceConfig config;
    private final SnapshotSplitReader snapshots;
    private final MySqlBinlogSplitReadTask binlog;
    private final Callable<Void> afterSnapshot;
    private final Callable<Void> afterLowWatermark;
    private final Callable<Void> afterLow;
    private volatile Statement watermarkStatement;
    private volatile boolean cancelled;
    private boolean started;
    private Map<String, Object> partition;

    public MySqlSnapshotSplitReadTask(MySqlSourceConfig config, int reader) {
        this(config, reader, () -> null);
    }

    /** A package-local phase callback lets database tests place commits exactly inside the scan window. */
    public MySqlSnapshotSplitReadTask(MySqlSourceConfig config, int reader, Callable<Void> afterSnapshot) {
        this(config, reader, afterSnapshot, () -> null, () -> null);
    }

    /** Executes after Low but before the consistent read view is established. */
    public MySqlSnapshotSplitReadTask(
            MySqlSourceConfig config,
            int reader,
            Callable<Void> afterSnapshot,
            Callable<Void> afterLowWatermark,
            Callable<Void> afterLow) {
        this.config = Objects.requireNonNull(config, "config");
        this.afterSnapshot = Objects.requireNonNull(afterSnapshot, "afterSnapshot");
        this.afterLowWatermark = Objects.requireNonNull(afterLowWatermark, "afterLowWatermark");
        this.afterLow = Objects.requireNonNull(afterLow, "afterLow");
        snapshots = new SnapshotSplitReader(config);
        binlog = new MySqlBinlogSplitReadTask(config.withoutSchemaEvolution(), config.snapshotServerId(reader));
    }

    /** Preserves the callback after the view is anchored for existing integration scenarios. */
    public MySqlSnapshotSplitReadTask(
            MySqlSourceConfig config, int reader, Callable<Void> afterSnapshot, Callable<Void> afterLow) {
        this(config, reader, afterSnapshot, () -> null, afterLow);
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
        if (split.chunkRange() == null) {
            throw new IllegalArgumentException("MySQL snapshot execution requires a typed ChunkRange");
        }
        ensureBinlog();
        if (config.tableOptions(split.tableId()).skipSnapshot()) {
            DebeziumOffset boundary = watermark();
            byte[] schemaHistory = binlog.snapshotHistory();
            if (schemaHistory.length == 0) {
                throw new IllegalStateException("Skipped MySQL Snapshot lacks recoverable Schema History");
            }
            return split.withOutput(new MySqlSnapshotOutput(boundary, boundary, java.util.List.of(), schemaHistory));
        }
        TableSchema schema = Objects.requireNonNull(config.schemas().get(split.tableId()), "chunk schema");
        // Flink CDC retains the normalized split in memory until bounded backfill completes.
        // Use the existing 64 MiB per-chunk budget instead of rejecting valid, wide chunks
        // at the old hard-coded 4 MiB limit. Keep the fail-closed row/byte bound.
        SnapshotChunkBackfill buffer = new SnapshotChunkBackfill(split.chunkRange(), schema, MAX_ROWS);
        try {
            DebeziumOffset low = openConsistentSnapshot(split);
            afterLow.call();
            boolean complete = false;
            while (!complete) {
                checkCancelled();
                RecordsWithSplitIds<TableRecord> batch = snapshots.fetch();
                while (batch.nextSplit() != null) {
                    io.yak.ops.core.data.TableRecord record;
                    while ((record = batch.nextRecordFromSplit()) != null) {
                        buffer.addSnapshot(record);
                    }
                }
                complete = batch.finishedSplits().contains(split.splitId());
                batch.recycle();
            }
            afterSnapshot.call();
            // Match Flink CDC's skipSnapshotBackfill contract: High = Low when enabled.
            // Changes since Low may appear in the consistent view and be replayed by
            // the resumed Binlog Reader. This setting deliberately gives at-least-once
            // rather than a fully normalized snapshot.
            DebeziumOffset high = config.isSkipSnapshotBackfill() ? low : watermark();
            if (low.compareTo(high) > 0) {
                throw new IllegalStateException("MySQL snapshot watermarks moved backwards");
            }
            if (low.compareTo(high) != 0) {
                long deadline = System.nanoTime() + BINLOG_TIMEOUT_NANOS;
                DebeziumOffset lastBackfillOffset = low;
                while (true) {
                    checkDeadline(deadline);
                    BinlogEvent event = binlog.poll();
                    if (event == null) continue;
                    // Upstream's bounded backfill consumes an ordered Debezium stream.
                    // Reordering within (LOW, HIGH] would cause overwritten snapshot
                    // images and cannot be repaired by a later checkpoint.
                    if (event.offset().compareTo(lastBackfillOffset) < 0) {
                        throw new IllegalStateException("MySQL Snapshot Backfill events are out of Binlog order");
                    }
                    lastBackfillOffset = event.offset();
                    if (backfillEvent(event, low, high, split.tableId(), buffer)) break;
                }
            }
            // Use the history of the actual bounded backfill task. A fresh no_data
            // bootstrap *after* Snapshot would describe a later schema and cannot
            // safely be backdated to the minimum completed chunk watermark.
            byte[] schemaHistory = binlog.snapshotHistory();
            if (schemaHistory.length == 0) {
                throw new IllegalStateException("Completed MySQL Snapshot lacks recoverable Schema History");
            }
            MySqlSnapshotSplit prepared =
                    split.withOutput(new MySqlSnapshotOutput(low, high, buffer.finish(), schemaHistory));
            return prepared;
        } finally {
            snapshots.close();
        }
    }

    /**
     * Applies one event from the Flink CDC-style bounded LOW-to-HIGH backfill interval.
     *
     * <p>The HIGH position is inclusive, and an event after HIGH marks completion
     * without applying its changes. Only events for this table can change the
     * snapshot buffer; mixed-table groups must fail closed instead of being dropped.
     * Returning true is equivalent to reaching the backfill BINLOG_END boundary.
     */
    static boolean backfillEvent(
            BinlogEvent event, DebeziumOffset low, DebeziumOffset high, TableId table, SnapshotChunkBackfill buffer) {
        Objects.requireNonNull(event, "event");
        int highOrder = event.offset().compareTo(high);
        if (event.offset().compareTo(low) > 0 && highOrder <= 0) {
            if (event.schemaChange() != null
                    && table.equals(event.schemaChange().tableId())) {
                throw new UnsupportedOperationException(
                        "Typed snapshot cannot reconcile schema changes during Backfill");
            }
            if (event.records().stream().anyMatch(record -> table.equals(record.tableId()))) {
                buffer.apply(event);
            }
        }
        return highOrder >= 0;
    }

    private void ensureBinlog() throws Exception {
        if (partition != null) return;
        if (!started) {
            started = true;
            binlog.start(new MySqlBinlogSplit(null, new byte[0]));
        }
        long deadline = System.nanoTime() + BINLOG_TIMEOUT_NANOS;
        while (partition == null) {
            checkDeadline(deadline);
            BinlogEvent event = binlog.poll();
            if (event != null && binlog.snapshotHistory().length > 0)
                partition = event.offset().partition();
        }
    }

    private DebeziumOffset watermark() throws SQLException {
        try (Connection connection = config.openSnapshotConnection()) {
            return watermark(connection);
        }
    }

    /**
     * Captures Low before opening a repeatable-read consistent snapshot.
     *
     * <p>Transactions committed between the Low watermark and read-view creation may be
     * visible in both the snapshot and the Low-to-High Binlog interval. Chunk Backfill
     * reconciles those changes; a global read lock is not required to close this window.
     */
    DebeziumOffset openConsistentSnapshot(MySqlSnapshotSplit split) throws SQLException {
        checkCancelled();
        DebeziumOffset low;
        try {
            low = watermark();
            afterLowWatermark.call();
            checkCancelled();
        } catch (Exception failure) {
            throw new SQLException("Cannot capture typed snapshot Low watermark", failure);
        }
        openTypedSnapshotReadView(split);
        return low;
    }

    /** Opens and pins the typed InnoDB view without requiring a global table lock. */
    public void openTypedSnapshotReadView(MySqlSnapshotSplit split) throws SQLException {
        checkCancelled();
        Connection view = null;
        try {
            view = config.openSnapshotConnection();
            view.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            view.setReadOnly(true);
            view.setAutoCommit(false);
            try (Statement transaction = view.createStatement()) {
                transaction.setQueryTimeout(60);
                transaction.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
            }
            // SnapshotSplitReader takes ownership of the pinned connection on success.
            snapshots.open(split, view);
        } catch (SQLException | RuntimeException failure) {
            if (view != null) {
                try {
                    view.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw new SQLException(
                    "Cannot establish a consistent typed snapshot read view; "
                            + "MySQL REPEATABLE READ and SELECT privileges are required",
                    failure);
        }
    }

    private DebeziumOffset watermark(Connection connection) throws SQLException {
        checkCancelled();
        try (Statement statement = connection.createStatement()) {
            watermarkStatement = statement;
            statement.setQueryTimeout(60);
            checkCancelled();
            BinlogOffset current = DebeziumUtils.currentBinlogOffset(statement);
            checkCancelled();
            Map<String, Object> position = new LinkedHashMap<>();
            position.put("file", current.getFilename());
            position.put("pos", current.getPosition());
            String gtids = current.getGtidSet();
            if (gtids != null && !gtids.isBlank()) position.put("gtids", gtids);
            return new DebeziumOffset(partition, position);
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
        if (cancelled || Thread.currentThread().isInterrupted())
            throw new SQLException("MySQL chunk backfill was cancelled");
    }

    public void cancel() {
        cancelled = true;
        snapshots.cancel();
        binlog.requestStop();
        Statement running = watermarkStatement;
        if (running != null)
            Thread.ofVirtual().name("yak-mysql-watermark-cancel").start(() -> {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    /* The owning fetcher releases JDBC resources in finally. */
                }
            });
    }

    @Override
    public void close() throws Exception {
        cancel();
        Exception failure = null;
        try {
            snapshots.close();
        } catch (Exception error) {
            failure = error;
        }
        try {
            binlog.close();
        } catch (Exception error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
}
