package io.yak.ops.task.executor;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.task.executor.eventbus.TaskExecutorEventBus;

public interface ITaskExecutor extends ITaskExecutorStateTracker {

    /**
     * Get the id of the task executor.
     * <p> Each task executor has a unique id.
     */
    default Integer getId() {
        return getTaskExecutionContext().getTaskInstanceId();
    }

    /**
     * Get the name of the task executor.
     */
    default String getName() {
        return getTaskExecutionContext().getTaskName();
    }

    /**
     * Get the type of the task executor.
     */
    default String getTaskType() {
        return getTaskExecutionContext().getTaskType();
    }

    /**
     * Get the TaskExecutionContext of the task executor.
     */
    TaskExecutionContext getTaskExecutionContext();

    /**
     * Whether the task executor is started.
     */
    boolean isStarted();

    /**
     * Start the task executor.
     */
    void start();

    /**
     * Pause the task executor.
     */
    void pause();

    /**
     * Kill the task executor.
     */
    void kill();

    /**
     * finalize the task executor.
     */
    void finalizeTask();

    /**
     * Get the EventBus belongs to the task executor.
     */
    TaskExecutorEventBus getTaskExecutorEventBus();

}
