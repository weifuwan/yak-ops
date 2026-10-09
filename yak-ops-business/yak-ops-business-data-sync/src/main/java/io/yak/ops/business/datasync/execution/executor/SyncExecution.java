package io.yak.ops.business.datasync.execution.executor;

import java.util.Optional;

/** 一次同步执行的句柄契约；当前不提供运行时实现。 */
public interface SyncExecution {

    ExecutionStatus status();

    ExecutionMetrics metrics();

    Optional<Throwable> failure();

    void cancel();

    ExecutionStatus await() throws InterruptedException;
}
