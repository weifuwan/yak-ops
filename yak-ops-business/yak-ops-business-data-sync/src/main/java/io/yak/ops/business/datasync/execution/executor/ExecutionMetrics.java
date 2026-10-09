package io.yak.ops.business.datasync.execution.executor;

/** 产品层保留的单次运行读取、写入行数。 */
public record ExecutionMetrics(long readRows, long writeRows) {

    public ExecutionMetrics {
        if (readRows < 0 || writeRows < 0) {
            throw new IllegalArgumentException("execution metrics must not be negative");
        }
    }
}
