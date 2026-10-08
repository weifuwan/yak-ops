package io.yak.ops.business.datasync.execution.executor;

import java.util.function.Consumer;

/**
 * 对活动 YakFlow Runtime 以固定间隔写入指标镜像，并在终态等待结束后最后刷新一次。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
final class ExecutionMetricsPoller {

    private static final long METRICS_FLUSH_INTERVAL_MILLIS = 500L;

    private ExecutionMetricsPoller() {}

    static ExecutionObservation awaitTermination(LocalExecution<?> execution, Consumer<ExecutionMetrics> metricsSink)
            throws InterruptedException {
        while (execution.status() == ExecutionStatus.RUNNING) {
            metricsSink.accept(execution.metrics());
            Thread.sleep(METRICS_FLUSH_INTERVAL_MILLIS);
        }
        ExecutionStatus status = execution.await();
        ExecutionMetrics metrics = execution.metrics();
        metricsSink.accept(metrics);
        return new ExecutionObservation(status, metrics);
    }
}
