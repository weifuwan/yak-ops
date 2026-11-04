package io.yak.ops.server.master.engine.task.client;

import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.exception.dispatch.TaskDispatchException;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;

/**
 * The interface of task executor client delegator. It is used to send operation to task executor server.
 *
 * @see LogicTaskExecutorClientDelegator
 * @see PhysicalTaskExecutorClientDelegator
 */
public interface ITaskExecutorClientDelegator {

    /**
     * Dispatch the task to task executor.
     *
     * @throws TaskDispatchException If dispatch failed
     */
    void dispatch(final ITaskExecution taskExecution) throws TaskDispatchException;

    /**
     * Take over the task from task executor.
     */
    boolean reassignMasterHost(final ITaskExecution taskExecution);

    /**
     * Pause the task, this method doesn't guarantee the task is paused success.
     */
    void pause(final ITaskExecution taskExecution);

    /**
     * Kill the task, this method doesn't guarantee the task is killed success.
     */
    void kill(final ITaskExecution taskExecution);

    /**
     * Ack the task executor lifecycle event.
     */
    void ackTaskExecutorLifecycleEvent(
                                       final ITaskExecution taskExecution,
                                       final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck);
}
