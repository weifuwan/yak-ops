package io.yak.ops.server.master.engine.task.statemachine;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskDispatchLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskDispatchedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFailedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFailoverLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFatalLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskKillLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskKilledLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskPauseLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskPausedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRetryLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRunningLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRuntimeContextChangedEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskStartLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskSuccessLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

/**
 * Represents the action to be taken when a task is in a certain state and receive a target event.
 * <p> Each {@link TaskExecutionStatus} should have a corresponding {@link ITaskStateAction} implementation.
 *
 * @see TaskSubmittedStateAction
 * @see TaskDelayExecutionStateAction
 * @see TaskDispatchStateAction
 * @see TaskRunningStateAction
 * @see TaskPauseStateAction
 * @see TaskKillStateAction
 * @see TaskFailureStateAction
 * @see TaskSuccessStateAction
 * @see TaskForceSuccessStateAction
 * @see TaskFailoverStateAction
 */
public interface ITaskStateAction {

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskStartLifecycleEvent}.
     * <p> This method is called when you want to start a task.
     */
    void onStartEvent(final IWorkflowExecution workflowExecution,
                      final ITaskExecution taskExecution,
                      final TaskStartLifecycleEvent taskStartEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskRunningLifecycleEvent}.
     * <p> This method is called when the master receive task running event from executor.
     */
    void onStartedEvent(final IWorkflowExecution workflowExecution,
                        final ITaskExecution taskExecution,
                        final TaskRunningLifecycleEvent taskRunningEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskRuntimeContextChangedEvent}.
     * <p> This method is called when the master receive task runtime context changed event from executor.
     */
    void onRuntimeContextChangedEvent(final IWorkflowExecution workflowExecution,
                                      final ITaskExecution taskExecution,
                                      final TaskRuntimeContextChangedEvent taskRuntimeContextChangedEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskRetryLifecycleEvent}.
     * <p> This method is called when the task need to retry.
     */
    void onRetryEvent(final IWorkflowExecution workflowExecution,
                      final ITaskExecution taskExecution,
                      final TaskRetryLifecycleEvent taskRetryEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskDispatchLifecycleEvent}.
     * <p> This method is called when you want to dispatch a task.
     */
    void onDispatchEvent(final IWorkflowExecution workflowExecution,
                         final ITaskExecution taskExecution,
                         final TaskDispatchLifecycleEvent taskDispatchEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskFatalLifecycleEvent}.
     * <p> This method is called when the task encounters catastrophic failure (e.g., initialization failure).
     */
    void onFatalEvent(final IWorkflowExecution workflowExecution,
                      final ITaskExecution taskExecution,
                      final TaskFatalLifecycleEvent taskFatalEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskDispatchedLifecycleEvent}.
     * <p> This method is called when the task has been dispatched to executor.
     */
    void onDispatchedEvent(final IWorkflowExecution workflowExecution,
                           final ITaskExecution taskExecution,
                           final TaskDispatchedLifecycleEvent taskDispatchedEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskPauseLifecycleEvent}.
     * <p> This method is called when you want to pause a task.
     */
    void onPauseEvent(final IWorkflowExecution workflowExecution,
                      final ITaskExecution taskExecution,
                      final TaskPauseLifecycleEvent taskPauseEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskPausedLifecycleEvent}.
     * <p> This method is called when the task has been paused.
     */
    void onPausedEvent(final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskPausedLifecycleEvent taskPausedEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskKillLifecycleEvent}.
     * <p> This method is called when you want to kill a task.
     */
    void onKillEvent(final IWorkflowExecution workflowExecution,
                     final ITaskExecution taskExecution,
                     final TaskKillLifecycleEvent taskKillEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskKilledLifecycleEvent}.
     * <p> This method is called when the task has been killed.
     */
    void onKilledEvent(final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskKilledLifecycleEvent taskKilledEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskFailedLifecycleEvent}.
     * <p> This method is called when the task has been failed.
     */
    void onFailedEvent(final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskFailedLifecycleEvent taskFailedEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskSuccessLifecycleEvent}.
     * <p> This method is called when the task has been success.
     */
    void onSucceedEvent(final IWorkflowExecution workflowExecution,
                        final ITaskExecution taskExecution,
                        final TaskSuccessLifecycleEvent taskSuccessEvent);

    /**
     * Perform the necessary actions when the task in a certain state receive a {@link TaskFailoverLifecycleEvent}.
     * <p> This method is called when the task need to failover.
     */
    void onFailoverEvent(final IWorkflowExecution workflowExecution,
                         final ITaskExecution taskExecution,
                         final TaskFailoverLifecycleEvent taskFailoverEvent);

    /**
     * Get the {@link TaskExecutionStatus} that this action match.
     */
    TaskExecutionStatus matchState();
}
