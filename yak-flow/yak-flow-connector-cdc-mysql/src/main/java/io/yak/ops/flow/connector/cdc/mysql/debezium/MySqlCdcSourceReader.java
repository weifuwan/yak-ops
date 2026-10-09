package io.yak.ops.flow.connector.cdc.mysql.debezium;

import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.source.SourceReader;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSplit;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionRuntime;
import io.yak.ops.plugin.database.jdbc.JdbcEndpoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * 运行 Debezium Engine、把 SourceRecord 转成 YakRow，并在下游 checkpoint 完成后确认 Debezium offset。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class MySqlCdcSourceReader implements SourceReader<MySqlCdcSplit> {

    private static final long POLL_TIMEOUT_MILLIS = 100L;

    private final MySqlCdcSourceConfig config;
    private final JdbcConnectionRuntime connectionRuntime;
    private final DebeziumRecordConverter converter;
    private final BlockingQueue<DebeziumBatch> batches;
    private final Deque<DebeziumBatch> pendingBatches = new ArrayDeque<>();
    private final Map<Long, Map<DebeziumBatch, Integer>> acknowledgementPlans = new LinkedHashMap<>();

    private JdbcEndpoint endpoint;
    private DebeziumEngine<RecordChangeEvent<SourceRecord>> engine;
    private Thread engineThread;
    private DebeziumBatch currentBatch;
    private SourceRecord lastDeliveredRecord;
    private volatile Throwable engineFailure;
    private volatile boolean closing;

    public MySqlCdcSourceReader(MySqlCdcSourceConfig config, JdbcConnectionRuntime connectionRuntime) {
        this.config = config;
        this.connectionRuntime = connectionRuntime;
        this.converter = new DebeziumRecordConverter(config.schema());
        this.batches = new ArrayBlockingQueue<>(config.queueCapacity());
    }

    @Override
    public void open(MySqlCdcSplit split) throws Exception {
        if (!config.table().equals(split.table())) {
            throw new IllegalArgumentException("MySQL CDC split 与配置表不匹配");
        }

        endpoint = connectionRuntime.openEndpoint(config.connection(), config.timeoutSeconds());
        engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(MySqlDebeziumEngineConfig.build(config, endpoint))
                .notifying(this::handleBatch)
                .build();
        engineThread =
                Thread.ofVirtual().name("yak-flow-mysql-cdc-" + config.name()).start(this::runEngine);
    }

    @Override
    public List<YakRow> poll() throws Exception {
        checkEngineFailure();
        List<YakRow> rows = new ArrayList<>(config.pollBatchSize());

        while (rows.size() < config.pollBatchSize()) {
            if (currentBatch == null) {
                currentBatch = batches.poll(rows.isEmpty() ? POLL_TIMEOUT_MILLIS : 0, TimeUnit.MILLISECONDS);
                if (currentBatch == null) break;
                pendingBatches.addLast(currentBatch);
            }

            if (!currentBatch.hasNext()) {
                currentBatch = null;
                continue;
            }

            RecordChangeEvent<SourceRecord> event = currentBatch.peekNext();
            List<YakRow> converted = converter.convert(event.record());
            if (!rows.isEmpty() && rows.size() + converted.size() > config.pollBatchSize()) {
                break;
            }

            currentBatch.markDelivered();
            lastDeliveredRecord = event.record();
            rows.addAll(converted);
        }

        checkEngineFailure();
        return rows;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public CheckpointState snapshotState(long checkpointId) {
        Map<DebeziumBatch, Integer> plan = new IdentityHashMap<>();
        for (DebeziumBatch batch : pendingBatches) {
            if (batch.deliveredCount() > 0 && !batch.fullyAcknowledged()) {
                plan.put(batch, batch.deliveredCount());
            }
        }
        acknowledgementPlans.put(checkpointId, plan);

        if (lastDeliveredRecord == null) {
            return MySqlCdcCheckpointState.empty();
        }
        return new MySqlCdcCheckpointState(
                copy(lastDeliveredRecord.sourcePartition()), copy(lastDeliveredRecord.sourceOffset()));
    }

    @Override
    public void restore(CheckpointState state) {
        if (!(state instanceof MySqlCdcCheckpointState)) {
            throw new IllegalArgumentException("MySQL CDC checkpoint state 类型不匹配");
        }
        // Durable recovery is owned by Debezium FileOffsetBackingStore and FileSchemaHistory.
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        Map<DebeziumBatch, Integer> plan = acknowledgementPlans.remove(checkpointId);
        if (plan == null) return;

        for (Map.Entry<DebeziumBatch, Integer> entry : plan.entrySet()) {
            entry.getKey().acknowledgeThrough(entry.getValue());
        }
        pendingBatches.removeIf(DebeziumBatch::fullyAcknowledged);
    }

    @Override
    public void close() throws Exception {
        closing = true;
        Exception failure = null;
        try {
            if (engine != null) engine.close();
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            if (engineThread != null) engineThread.join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (failure == null) failure = exception;
        }
        try {
            if (endpoint != null) endpoint.close();
        } catch (Exception exception) {
            if (failure == null) failure = exception;
        }
        if (failure != null) throw failure;
    }

    private void handleBatch(
            List<RecordChangeEvent<SourceRecord>> records,
            DebeziumEngine.RecordCommitter<RecordChangeEvent<SourceRecord>> committer)
            throws InterruptedException {
        if (records.isEmpty()) {
            committer.markBatchFinished();
            return;
        }
        batches.put(new DebeziumBatch(records, committer));
    }

    private void runEngine() {
        try {
            engine.run();
            if (!closing) {
                engineFailure = new IllegalStateException("Debezium Engine stopped unexpectedly");
            }
        } catch (Throwable throwable) {
            if (!closing) {
                engineFailure = throwable;
            }
        }
    }

    private void checkEngineFailure() throws Exception {
        Throwable failure = engineFailure;
        if (failure == null) return;
        if (failure instanceof Exception exception) throw exception;
        throw new IllegalStateException("Debezium Engine failed", failure);
    }

    private Map<String, Object> copy(Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (values != null) {
            values.forEach(copy::put);
        }
        return copy;
    }
}
