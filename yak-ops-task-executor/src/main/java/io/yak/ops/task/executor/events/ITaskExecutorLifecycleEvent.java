package io.yak.ops.task.executor.events;

import io.yak.ops.task.executor.ITaskExecutor;

/**
 * The lifecycle event of {@link ITaskExecutor}, all the lifecycle event should implement this interface.
 */
public interface ITaskExecutorLifecycleEvent {

    int getTaskInstanceId();

    long getEventCreateTime();

    TaskExecutorLifecycleEventType getType();

}
