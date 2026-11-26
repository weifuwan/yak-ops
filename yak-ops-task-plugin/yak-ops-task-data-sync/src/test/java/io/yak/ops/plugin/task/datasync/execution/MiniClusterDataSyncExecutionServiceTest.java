package io.yak.ops.plugin.task.datasync.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class MiniClusterDataSyncExecutionServiceTest {

    @Test
    void ownsMiniClusterUntilExecutionIsCancelledAndReleased() throws Exception {
        StreamExecutionEnvironment environment =
                StreamExecutionEnvironment.createLocalEnvironment(1, new Configuration());
        environment.setParallelism(1);
        environment.addSource(new BlockingSource())
                .name("data-sync-lifecycle-source")
                .addSink(new RichSinkFunction<Long>() {

                    @Override
                    public void invoke(Long value, Context context) {
                        // The test only needs a running sink operator.
                    }
                })
                .name("data-sync-lifecycle-sink");

        MiniClusterDataSyncExecutionService service = new MiniClusterDataSyncExecutionService();
        FlinkPipelineExecution pipeline = new FlinkPipelineExecution(environment, "data-sync-p0", true);
        DataSyncExecutionHandle handle = service.submit(pipeline, "execution-p1", 42);
        try {
            assertNotNull(handle.getJobId());
            assertTrue(handle.getRestEndpoint().startsWith("http://"));
            assertEquals(DataSyncExecutionState.RUNNING, service.queryState(handle));

            service.cancel(handle);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            DataSyncExecutionState state = service.queryState(handle);
            while (state != DataSyncExecutionState.CANCELED && System.nanoTime() < deadline) {
                Thread.sleep(100L);
                state = service.queryState(handle);
            }
            assertEquals(DataSyncExecutionState.CANCELED, state);
        } finally {
            service.release(handle);
        }
    }

    private static final class BlockingSource extends RichParallelSourceFunction<Long> {

        private volatile boolean running = true;

        @Override
        public void run(SourceContext<Long> context) throws Exception {
            long value = 0L;
            while (running) {
                synchronized (context.getCheckpointLock()) {
                    context.collect(value++);
                }
                Thread.sleep(50L);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}
