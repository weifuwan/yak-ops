package io.yak.ops.server.master.engine.task.statemachine;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.plugin.task.api.utils.VarPoolUtils;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskDispatchLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskDispatchedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFailedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFailoverLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskKillLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskKilledLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskPauseLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskPausedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRetryLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRunningLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskStartLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskSuccessLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The action to be taken when a task is in the Success state.
 */
@Slf4j
@Component
public class TaskSuccessStateAction extends AbstractTaskStateAction {

    @Override
    public void onStartEvent(final IWorkflowExecution workflowExecution,
                             final ITaskExecution taskExecution,
                             final TaskStartLifecycleEvent taskStartEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        final TaskSuccessLifecycleEvent taskSuccessLifecycleEvent = TaskSuccessLifecycleEvent.builder()
                .taskExecution(taskExecution)
                .varPool(VarPoolUtils.deserializeVarPool(taskExecution.getTaskInstance().getVarPool()))
                .endTime(taskExecution.getTaskInstance().getEndTime())
                .build();
        super.onSucceedEvent(workflowExecution, taskExecution, taskSuccessLifecycleEvent);
    }

    @Override
    public void onStartedEvent(final IWorkflowExecution workflowExecution,
                               final ITaskExecution taskExecution,
                               final TaskRunningLifecycleEvent taskRunningEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskRunningEvent);
    }

    @Override
    public void onRetryEvent(final IWorkflowExecution workflowExecution,
                             final ITaskExecution taskExecution,
                             final TaskRetryLifecycleEvent taskRetryEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskRetryEvent);
    }

    @Override
    public void onDispatchEvent(final IWorkflowExecution workflowExecution,
                                final ITaskExecution taskExecution,
                                final TaskDispatchLifecycleEvent taskDispatchEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskDispatchEvent);
    }

    @Override
    public void onDispatchedEvent(final IWorkflowExecution workflowExecution,
                                  final ITaskExecution taskExecution,
                                  final TaskDispatchedLifecycleEvent taskDispatchedEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskDispatchedEvent);
    }

    @Override
    public void onPauseEvent(final IWorkflowExecution workflowExecution,
                             final ITaskExecution taskExecution,
                             final TaskPauseLifecycleEvent taskPauseEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskPauseEvent);
    }

    @Override
    public void onPausedEvent(final IWorkflowExecution workflowExecution,
                              final ITaskExecution taskExecution,
                              final TaskPausedLifecycleEvent taskPausedEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskPausedEvent);
    }

    @Override
    public void onKillEvent(final IWorkflowExecution workflowExecution,
                            final ITaskExecution taskExecution,
                            final TaskKillLifecycleEvent taskKillEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskKillEvent);
    }

    @Override
    public void onKilledEvent(final IWorkflowExecution workflowExecution,
                              final ITaskExecution taskExecution,
                              final TaskKilledLifecycleEvent taskKilledEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskKilledEvent);
    }

    @Override
    public void onFailedEvent(final IWorkflowExecution workflowExecution,
                              final ITaskExecution taskExecution,
                              final TaskFailedLifecycleEvent taskFailedEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskFailedEvent);
    }

    @Override
    public void onSucceedEvent(final IWorkflowExecution workflowExecution,
                               final ITaskExecution taskExecution,
                               final TaskSuccessLifecycleEvent taskSuccessEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskSuccessEvent);
    }

    @Override
    public void onFailoverEvent(final IWorkflowExecution workflowExecution,
                                final ITaskExecution taskExecution,
                                final TaskFailoverLifecycleEvent taskFailoverEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        logWarningIfCannotDoAction(taskExecution, taskFailoverEvent);
    }

    @Override
    public TaskExecutionStatus matchState() {
        return TaskExecutionStatus.SUCCESS;
    }
}
