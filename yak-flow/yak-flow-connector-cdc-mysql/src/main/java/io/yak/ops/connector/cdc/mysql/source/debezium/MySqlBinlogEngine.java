package io.yak.ops.connector.cdc.mysql.source.debezium;

import io.debezium.embedded.Connect;
import io.debezium.engine.ChangeEventFormat;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.reader.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlDebeziumRecordConverter;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

    public MySqlBinlogEngine(MySqlCdcSourceConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        converter = new MySqlDebeziumRecordConverter(config.schemas());
        queue = new ArrayBlockingQueue<>(config.queueCapacity());
    }

    /** Starts the engine, optionally seeding its offset and Schema History from a checkpoint. */
    public void start(MySqlBinlogSplit split) throws IOException {
        if (started || stopped) {
            throw new IllegalStateException("MySQL Binlog engine cannot start twice");
        }
        started = true;
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
        engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(config.debeziumProperties(sessionId, historyPath))
                .notifying(this::handleBatch)
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
            BinlogEvent converted;
            try {
                converted = converter.convert(event.record());
            } catch (RuntimeException error) {
                failure.compareAndSet(null, error);
                throw new IllegalStateException("MySQL CDC event conversion failed", error);
            }
            if (converted != null) {
                while (!stopped && !queue.offer(converted, 250, TimeUnit.MILLISECONDS)) {
                    // Bounded handover: do not allow Debezium prefetch to grow without limit.
                }
                if (stopped) {
                    throw new InterruptedException("MySQL Binlog reader was stopped");
                }
            }
            // The engine may stage this offset, but the YakFlow checkpoint uses only the
            // last event delivered by MySqlCdcRecordEmitter on the owning task mailbox.
            committer.markProcessed(event);
        }
        committer.markBatchFinished();
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
     * <p>SQL Schema Evolution is unsupported in PR1. The history is essential for
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
