package io.yak.ops.worker.task.executor;

import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBus;

public interface ITaskExecutor extends ITaskExecutorStateTracker {
    default String getId() {
        return getTaskExecutionContext().getTaskInstanceId();
    }

    default String getName() {
        return getTaskExecutionContext().getTaskName();
    }

    default String getTaskType() {
        return getTaskExecutionContext().getTaskType();
    }

    TaskExecutionContext getTaskExecutionContext();

    boolean isStarted();

    void start();

    void pause();

    void kill();

    void finalizeTask();

    TaskExecutorEventBus getTaskExecutorEventBus();
}
