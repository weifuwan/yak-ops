package io.yak.ops.business.datasync.execution.executor;

/** 产品层使用的执行状态；不包含同步引擎实现。 */
public enum ExecutionStatus {
    CREATED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }
}
