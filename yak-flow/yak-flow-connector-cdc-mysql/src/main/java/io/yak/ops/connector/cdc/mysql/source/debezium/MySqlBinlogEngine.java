package io.yak.ops.connector.cdc.mysql.source.debezium;

import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.reader.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlDebeziumRecordConverter;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableMetadata;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * Owns one Debezium engine and bounded handover queue for a MySQL Binlog split.
 *
 * <p>The engine never writes records or checkpoint offsets into the Runtime mailbox.
 * It only enqueues detached complete change events. Its private volatile offsets may run
 * ahead of emission, but are never promoted to a completed YakFlow checkpoint.
 */
public final class MySqlBinlogEngine implements AutoCloseable {

    private final MySqlCdcSourceConfig config;
    private final int serverId;
    private final MySqlDebeziumRecordConverter converter;
    private final BlockingQueue<BinlogEvent> queue;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    private DebeziumEngine<RecordChangeEvent<SourceRecord>> engine;
    private Thread worker;
    private Path directory;
    private Path historyPath;
    private String sessionId;
    private volatile boolean stopped;
    private boolean started;
    private BinlogOffset lastQueuedOffset;

    public MySqlBinlogEngine(MySqlCdcSourceConfig config) {
        this(config, config.serverId());
    }

    /** Uses a distinct replication connection while retaining the source partition and table contract. */
    public MySqlBinlogEngine(MySqlCdcSourceConfig config, int serverId) {
        if (serverId < 1) throw new IllegalArgumentException("Replication server ID must be positive");
        this.serverId = serverId;
        this.config = Objects.requireNonNull(config, "config");
        converter = new MySqlDebeziumRecordConverter(
                config.schemas(), config.physicalSchemas(), config.schemaEvolutionEnabled());
        queue = new ArrayBlockingQueue<>(config.queueCapacity());
    }

    /** Starts the engine, optionally seeding its offset and Schema History from a checkpoint. */
    public void start(MySqlBinlogSplit split) throws IOException {
        if (started || stopped) {
            throw new IllegalStateException("MySQL Binlog engine cannot start twice");
        }
        started = true;
        lastQueuedOffset = split.offset();
        directory = Files.createTempDirectory("yakflow-binlog-");
        historyPath = directory.resolve("schema-history.dat");
        byte[] history = split.schemaHistory();
        if (split.offset() != null && history.length == 0) {
            throw new IllegalArgumentException("A restored MySQL Binlog offset requires Schema History");
        }
        if (history.length != 0) {
            Files.write(historyPath, history);
        }
        String connectorName = "yakflow-cdc-" + config.topicPrefix();
        sessionId = CheckpointOffsetBackingStore.register(connectorName, split.offset());
        var properties = config.debeziumProperties(sessionId, historyPath);
        properties.setProperty("database.server.id", Integer.toString(serverId));
        // Snapshot readers share a logical source but must own distinct JMX registrations.
        properties.setProperty("custom.metric.tags", "reader=" + serverId);
        engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(properties)
                .notifying(this::handleBatch)
                .using((success, message, error) -> {
                    if (!success && !stopped) {
                        // Preserve the original driver exception instead of only reporting an engine stop.
                        failure.compareAndSet(
                                null,
                                error != null
                                        ? error
                                        : new IllegalStateException("Debezium MySQL connector failed: " + message));
                    }
                })
                .build();
        worker = Thread.ofVirtual().name("yakflow-mysql-debezium").start(() -> {
            try {
                engine.run();
                if (!stopped) {
                    failure.compareAndSet(
                            null, new IllegalStateException("Debezium MySQL Binlog engine stopped unexpectedly"));
                }
            } catch (Throwable error) {
                if (!stopped) {
                    failure.compareAndSet(null, error);
                }
            }
        });
    }

    private void handleBatch(
            java.util.List<RecordChangeEvent<SourceRecord>> events,
            DebeziumEngine.RecordCommitter<RecordChangeEvent<SourceRecord>> committer)
            throws InterruptedException {
        for (RecordChangeEvent<SourceRecord> event : events) {
            if (stopped) {
                throw new InterruptedException("MySQL Binlog reader was stopped");
            }
            try {
                SourceRecord source = event.record();
                TableId changed = converter.schemaMismatch(source);
                if (changed != null) {
                    // A Debezium DDL with empty tableChanges must not cause the next
                    // row to bypass SchemaCoordinator. Read metadata, verify that it
                    // describes THIS row, and emit the schema boundary before the row.
                    // It uses the previous offset: acknowledging the boundary must
                    // not checkpoint past the still-pending row.
                    BinlogEvent transition = reconcile(source, changed);
                    enqueue(transition);
                }
                BinlogEvent converted = converter.convert(source);
                if (converted != null) {
                    enqueue(converted);
                    lastQueuedOffset = converted.offset();
                }
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (Exception error) {
                failure.compareAndSet(null, error);
                throw new IllegalStateException("MySQL CDC event conversion failed", error);
            }
            // The engine may stage this offset, but the YakFlow checkpoint uses only
            // the last event delivered by the owning Source mailbox.
            committer.markProcessed(event);
        }
        committer.markBatchFinished();
    }

    private BinlogEvent reconcile(SourceRecord source, TableId table) throws SQLException {
        if (lastQueuedOffset == null) {
            throw new IllegalStateException("Cannot reconcile CDC schema before an established binlog offset");
        }
        TableSchema live;
        try (Connection connection = config.openSnapshotConnection()) {
            live = JdbcTableMetadata.readTable(
                    connection, JdbcDialects.forUrl(connection.getMetaData().getURL()), table);
        }
        Struct envelope = (Struct) source.value();
        Struct row = envelope.schema().field("after") == null ? null : envelope.getStruct("after");
        if (row == null && envelope.schema().field("before") != null) {
            row = envelope.getStruct("before");
        }
        if (row == null) {
            throw new IllegalStateException("CDC schema reconciliation requires a row image");
        }
        if (!converter.matchesRowSchema(row, live)) {
            throw new IllegalStateException(
                    "Current MySQL metadata does not match the binlog row; cannot safely recover schema");
        }
        BinlogOffset position = new BinlogOffset(
                new LinkedHashMap<String, Object>(source.sourcePartition()),
                new LinkedHashMap<String, Object>(source.sourceOffset()));
        return converter.reconcile(table, live, lastQueuedOffset, position);
    }

    private void enqueue(BinlogEvent record) throws InterruptedException {
        while (!stopped && !queue.offer(record, 250, TimeUnit.MILLISECONDS)) {
            // Bounded handover: preserve strict schema-event / row order.
        }
        if (stopped) {
            throw new InterruptedException("MySQL Binlog reader was stopped");
        }
    }

    public BinlogEvent poll() throws InterruptedException {
        Throwable error = failure.get();
        if (error != null) {
            throw new IllegalStateException("MySQL Binlog Debezium engine failed", error);
        }
        BinlogEvent record = queue.poll(250, TimeUnit.MILLISECONDS);
        error = failure.get();
        if (error != null) {
            throw new IllegalStateException("MySQL Binlog Debezium engine failed", error);
        }
        return record;
    }

    /**
     * Copies the local Schema History for the Reader's completed-checkpoint candidate.
     *
     * <p>Debezium Schema History is essential for
     * resuming Debezium at a saved Binlog offset without rebuilding an unrelated schema.
     */
    public byte[] snapshotHistory() throws IOException {
        if (historyPath == null || !Files.exists(historyPath)) {
            return new byte[0];
        }
        byte[] history = Files.readAllBytes(historyPath);
        if (history.length > 8 * 1024 * 1024) {
            throw new IOException("MySQL CDC Schema History checkpoint exceeds size limit");
        }
        return history;
    }

    public void requestStop() {
        if (stopped) {
            return;
        }
        stopped = true;
        DebeziumEngine<?> running = engine;
        if (running != null) {
            Thread.ofVirtual().name("yakflow-mysql-cdc-stop").start(() -> {
                try {
                    running.close();
                } catch (IOException ignored) {
                    // The fetcher's final close still bounds resource termination.
                }
            });
        }
    }

    @Override
    public void close() throws Exception {
        requestStop();
        if (worker != null) {
            worker.join(5_000);
            if (worker.isAlive()) {
                worker.interrupt();
                throw new IOException("Debezium MySQL engine failed to stop within timeout");
            }
        }
        if (sessionId != null) {
            CheckpointOffsetBackingStore.unregister(sessionId);
        }
        if (historyPath != null) {
            Files.deleteIfExists(historyPath);
        }
        if (directory != null) {
            Files.deleteIfExists(directory);
        }
    }
}
