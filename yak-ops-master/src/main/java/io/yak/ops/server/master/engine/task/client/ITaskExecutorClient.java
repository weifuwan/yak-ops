package io.yak.ops.server.master.engine.task.client;

import io.yak.ops.server.master.engine.exceptions.TaskKillException;
import io.yak.ops.server.master.engine.exceptions.TaskPauseException;
import io.yak.ops.server.master.engine.exceptions.TaskReassignMasterHostException;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.exception.dispatch.TaskDispatchException;
import io.yak.ops.task.executor.TaskEngine;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;

/**
 * The client used to communicate with {@link TaskEngine}.
 */
public interface ITaskExecutorClient {

    /**
     * Dispatch the task to task executor.
     *
     * @throws TaskDispatchException If dispatch failed or error occurs.
     */
    void dispatch(final ITaskExecution taskExecution) throws TaskDispatchException;

    /**
     * Reassign the workflow instance host from task executor.
     *
     * @throws TaskReassignMasterHostException If an error occurs.
     */
    boolean reassignWorkflowInstanceHost(final ITaskExecution taskExecution) throws TaskReassignMasterHostException;

    /**
     * Pause task from task executor.
     * <p> This method is not a sync method, it will return immediately after send a kill request to remote executor and receive a response.
     * but not guarantee the task will be paused.
     * <p> Not all task can support pause operation, if the task doesn't support pause, then it will just ignore the pause request, then you need to wait it finished.
     *
     * @throws TaskPauseException If an error occurs.
     */
    void pause(final ITaskExecution taskExecution) throws TaskPauseException;

    /**
     * Kill task from task executor.
     * <p> This method is not a sync method, it will return immediately after send a kill request to remote executor.
     * but not guarantee the task will be killed.
     * <p> All task should support kill operation, but some kill operation might cost long time, so this is unstable.
     *
     * @throws TaskKillException If an error occurs.
     */
    void kill(final ITaskExecution taskExecution) throws TaskKillException;

    /**
     * Send TaskExecutorLifecycleEventAck to TaskEngine.
     * <p> This method will not throw exception, once send ack failed, the executor engine will retry.
     */
    void ackTaskExecutorLifecycleEvent(
                                       final ITaskExecution taskExecution,
                                       final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck);

}
