package io.yak.ops.plugin.task.datasync.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionHandle;
import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionState;
import io.yak.ops.plugin.task.datasync.execution.MiniClusterDataSyncExecutionService;

import org.apache.flink.cdc.connectors.mysql.source.metrics.MySqlSourceReaderMetrics;

import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import io.debezium.data.Envelope;
import io.debezium.relational.TableId;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Exercises the production MiniCluster execution service and production REST metrics collector
 * together, including the namespace/schema/table metric groups used by the CDC source and JDBC sink.
 */
class FlinkRestDataSyncMetricsServiceMiniClusterTest {

    private static final long SOURCE_HOLD_MILLIS = 10_000L;
    private static final long TIMEOUT_SECONDS = 30L;

    @Test
    void collectsSingleTableSourceAndSinkMetricsWhileRunningAndAfterFinished() throws Exception {
        MiniClusterDataSyncExecutionService executionService =
                new MiniClusterDataSyncExecutionService();
        FlinkRestDataSyncMetricsService metricsService =
                new FlinkRestDataSyncMetricsService();
        DataSyncExecutionHandle handle = executionService.submit(
                createPipeline(), "execution-mini-cluster-metrics", 1);

        try {
            DataSyncExecutionSnapshot runningSnapshot =
                    awaitAvailableSnapshot(metricsService, handle);
            assertEquals(DataSyncExecutionState.RUNNING, executionService.queryState(handle));
            assertMetrics(runningSnapshot);

            awaitState(executionService, handle, DataSyncExecutionState.FINISHED);

            DataSyncExecutionSnapshot finalSnapshot = metricsService.collect(handle);
            assertMetrics(finalSnapshot);
        } finally {
            executionService.release(handle);
        }
    }

    private static FlinkPipelineExecution createPipeline() {
        StreamExecutionEnvironment environment =
                StreamExecutionEnvironment.createLocalEnvironment(1, new Configuration());
        environment.setParallelism(1);
        environment.disableOperatorChaining();
        environment.addSource(new TableMetricsSource(SOURCE_HOLD_MILLIS))
                .name("data-sync-metrics-source")
                .setParallelism(1)
                .addSink(new TableMetricsSink())
                .name("data-sync-metrics-sink")
                .setParallelism(1);
        return new FlinkPipelineExecution(environment, "data-sync-metrics-integration", true);
    }

    private static DataSyncExecutionSnapshot awaitAvailableSnapshot(
                                                                    FlinkRestDataSyncMetricsService metricsService,
                                                                    DataSyncExecutionHandle handle) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        Exception lastException = null;
        DataSyncExecutionSnapshot lastSnapshot = null;
        while (System.nanoTime() < deadline) {
            try {
                lastSnapshot = metricsService.collect(handle);
                if ("AVAILABLE".equals(lastSnapshot.getMetricsState())
                        && !lastSnapshot.getTables().isEmpty()) {
                    return lastSnapshot;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (Exception exception) {
                lastException = exception;
            }
            Thread.sleep(250L);
        }
        AssertionError failure = new AssertionError(
                "Timed out waiting for production Flink REST metrics; last metrics state="
                        + (lastSnapshot == null ? "<no snapshot>" : lastSnapshot.getMetricsState()));
        if (lastException != null) {
            failure.initCause(lastException);
        }
        throw failure;
    }

    private static void awaitState(
                                   MiniClusterDataSyncExecutionService executionService,
                                   DataSyncExecutionHandle handle,
                                   DataSyncExecutionState expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        DataSyncExecutionState actual = executionService.queryState(handle);
        while (actual != expected && System.nanoTime() < deadline) {
            Thread.sleep(100L);
            actual = executionService.queryState(handle);
        }
        assertEquals(expected, actual);
    }

    private static void assertMetrics(DataSyncExecutionSnapshot snapshot) {
        assertNotNull(snapshot);
        assertEquals("AVAILABLE", snapshot.getMetricsState());
        assertEquals(7L, snapshot.getReadRecords());
        assertEquals(3L, snapshot.getSnapshotRecords());
        assertEquals(4L, snapshot.getIncrementalRecords());
        assertEquals(2L, snapshot.getInsertRecords());
        assertEquals(1L, snapshot.getUpdateRecords());
        assertEquals(1L, snapshot.getDeleteRecords());
        assertEquals(7L, snapshot.getCommittedRecords());

        assertEquals(1, snapshot.getTables().size());
        DataSyncTableMetrics table = snapshot.getTables().get(0);
        assertEquals("db.orders", table.getSourceTable());
        assertEquals("db.orders", table.getSinkTable());
        assertEquals("AVAILABLE", table.getMetricsState());
        assertEquals(7L, table.getReadRecords());
        assertEquals(3L, table.getSnapshotRecords());
        assertEquals(4L, table.getIncrementalRecords());
        assertEquals(2L, table.getInsertRecords());
        assertEquals(1L, table.getUpdateRecords());
        assertEquals(1L, table.getDeleteRecords());
        assertEquals(7L, table.getCommittedRecords());
    }

    private static final class TableMetricsSource extends RichParallelSourceFunction<Long> {

        private final long holdMillis;
        private volatile boolean running = true;

        private TableMetricsSource(long holdMillis) {
            this.holdMillis = holdMillis;
        }

        @Override
        public void run(SourceContext<Long> context) throws Exception {
            MetricGroup root = getRuntimeContext().getMetricGroup();
            MySqlSourceReaderMetrics sourceMetrics = new MySqlSourceReaderMetrics(root);
            sourceMetrics.registerMetrics();
            TableId tableId = new TableId("db", null, "orders");

            for (long record = 0; record < 7L && running; record++) {
                synchronized (context.getCheckpointLock()) {
                    context.collect(record);
                    if (record < 3L) {
                        sourceMetrics.recordDataChange(tableId, Envelope.Operation.READ);
                    } else if (record < 5L) {
                        sourceMetrics.recordDataChange(tableId, Envelope.Operation.CREATE);
                    } else if (record == 5L) {
                        sourceMetrics.recordDataChange(tableId, Envelope.Operation.UPDATE);
                    } else {
                        sourceMetrics.recordDataChange(tableId, Envelope.Operation.DELETE);
                    }
                }
            }

            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(holdMillis);
            while (running && System.nanoTime() < deadline) {
                Thread.sleep(50L);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    private static final class TableMetricsSink extends RichSinkFunction<Long> {

        private transient Counter committedRecords;
        private transient Counter tableCommittedRecords;

        @Override
        public void invoke(Long value, Context context) {
            if (committedRecords == null) {
                MetricGroup root = getRuntimeContext().getMetricGroup();
                committedRecords = root.counter("numCommittedRecords");
                tableCommittedRecords = root.addGroup("namespace", "")
                        .addGroup("schema", "db")
                        .addGroup("table", "orders")
                        .counter("numCommittedRecords");
            }
            committedRecords.inc();
            tableCommittedRecords.inc();
        }
    }
}
