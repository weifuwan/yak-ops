package io.yak.ops.worker.task.executor.events;

import io.yak.ops.worker.task.executor.ITaskExecutor;

/** Immutable local task lifecycle event. */
public final class TaskExecutorLifecycleEvent {
    private final String taskInstanceId;
    private final TaskExecutorLifecycleEventType type;

    private TaskExecutorLifecycleEvent(String id, TaskExecutorLifecycleEventType type) {
        this.taskInstanceId = id;
        this.type = type;
    }

    public static TaskExecutorLifecycleEvent of(ITaskExecutor executor, TaskExecutorLifecycleEventType type) {
        return new TaskExecutorLifecycleEvent(executor.getId(), type);
    }

    public String getTaskInstanceId() {
        return taskInstanceId;
    }

    public TaskExecutorLifecycleEventType getType() {
        return type;
    }
}
