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
        MySqlChunkBackfill buffer = new MySqlChunkBackfill(
                split,
                Objects.requireNonNull(config.schemas().get(split.tableId()), "chunk schema"),
                MAX_ROWS,
                MAX_PAYLOAD_BYTES);
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
                    if (BinlogOffsetOrder.compare(event.offset(), low) > 0
                            && highOrder <= 0
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
            if (event != null && binlog.snapshotHistory().length > 0)
                partition = event.offset().partition();
        }
    }

    private BinlogOffset watermark() throws SQLException {
        checkCancelled();
        try (Connection connection = config.openSnapshotConnection();
                Statement statement = connection.createStatement()) {
            watermarkStatement = statement;
            statement.setQueryTimeout(60);
            checkCancelled();
            ResultSet rows;
            try {
                rows = statement.executeQuery("SHOW BINARY LOG STATUS");
            } catch (SQLException unsupported) {
                checkCancelled();
                try {
                    rows = statement.executeQuery("SHOW MASTER STATUS");
                } catch (SQLException failure) {
                    failure.addSuppressed(unsupported);
                    throw failure;
                }
            }
            try (ResultSet status = rows) {
                if (!status.next()) throw new SQLException("MySQL binary logging is disabled");
                String file = status.getString(1);
                long position = status.getLong(2);
                if (file == null || file.isBlank() || position < 1)
                    throw new SQLException("Invalid MySQL Binlog boundary");
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
                    /* Owning fetcher observes termination and releases resources. */
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
