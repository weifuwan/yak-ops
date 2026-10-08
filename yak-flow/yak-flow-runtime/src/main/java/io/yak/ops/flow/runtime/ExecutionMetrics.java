package io.yak.ops.flow.runtime;

/**
 * YakFlow 单次执行的轻量累计指标快照。
 *
 * @param readRows Source 已成功放入 Runtime Channel 的累计行数
 * @param writeRows SinkWriter.write 已成功接收的累计行数
 * @author weifuwan
 * @since 2026-09-27
 */
public record ExecutionMetrics(long readRows, long writeRows) {

    public ExecutionMetrics {
        if (readRows < 0 || writeRows < 0) {
            throw new IllegalArgumentException("execution metrics must not be negative");
        }
    }
}
