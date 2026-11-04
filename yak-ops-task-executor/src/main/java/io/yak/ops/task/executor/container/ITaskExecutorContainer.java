package io.yak.ops.task.executor.container;

import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.exceptions.TaskExecutorRuntimeException;

/**
 * The task executor container used to execute the submitted ILogicTaskExecutor.
 */
public interface ITaskExecutorContainer {

    /**
     * Dispatch the task executor to the container.
     * <p> If dispatch failed, throw TaskExecutorRuntimeException.
     */
    void dispatch(final ITaskExecutor taskExecutor) throws TaskExecutorRuntimeException;

    /**
     * Start the task executor in the container.
     */
    void start(final ITaskExecutor taskExecutor);

    /**
     * Pause the task executor by the task instance id.
     */
    void pause(final ITaskExecutor taskExecutor);

    /**
     * Kill the task executor by the task instance id.
     */
    void kill(final ITaskExecutor taskExecutor);

    /**
     * Finalize the task executor.
     */
    void finalize(final ITaskExecutor taskExecutor);

    /**
     * Get the task executor slot usage which can represent the load of the container.
     * <p> If the value is 1.0, the container is full.
     */
    double slotUsage();

}
