package io.yak.ops.server.master.engine.task.statemachine;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
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

@Slf4j
@Component
public class TaskRunningStateAction extends AbstractTaskStateAction {

    @Override
    public void onStartEvent(final IWorkflowExecution workflowExecution,
                             final ITaskExecution taskExecution,
                             final TaskStartLifecycleEvent taskStartEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        taskExecution.getWorkflowEventBus().publish(TaskFailoverLifecycleEvent.of(taskExecution));
    }

    @Override
    public void onStartedEvent(final IWorkflowExecution workflowExecution,
                               final ITaskExecution taskExecution,
                               final TaskRunningLifecycleEvent taskRunningEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        persistentTaskInstanceStartedEventToDB(taskExecution, taskRunningEvent);
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
        taskExecutorClient.pause(taskExecution);
    }

    @Override
    public void onPausedEvent(final IWorkflowExecution workflowExecution,
                              final ITaskExecution taskExecution,
                              final TaskPausedLifecycleEvent taskPausedEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        super.onPausedEvent(workflowExecution, taskExecution, taskPausedEvent);
    }

    @Override
    public void onKillEvent(final IWorkflowExecution workflowExecution,
                            final ITaskExecution taskExecution,
                            final TaskKillLifecycleEvent taskKillEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        taskExecutorClient.kill(taskExecution);
    }

    @Override
    public void onKilledEvent(final IWorkflowExecution workflowExecution,
                              final ITaskExecution taskExecution,
                              final TaskKilledLifecycleEvent taskKilledEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        super.onKilledEvent(workflowExecution, taskExecution, taskKilledEvent);
    }

    @Override
    public void onFailedEvent(final IWorkflowExecution workflowExecution,
                              final ITaskExecution taskExecution,
                              final TaskFailedLifecycleEvent taskFailedEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        super.onFailedEvent(workflowExecution, taskExecution, taskFailedEvent);
    }

    @Override
    public void onSucceedEvent(final IWorkflowExecution workflowExecution,
                               final ITaskExecution taskExecution,
                               final TaskSuccessLifecycleEvent taskSuccessEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        super.onSucceedEvent(workflowExecution, taskExecution, taskSuccessEvent);
    }

    @Override
    public void onFailoverEvent(final IWorkflowExecution workflowExecution,
                                final ITaskExecution taskExecution,
                                final TaskFailoverLifecycleEvent taskFailoverEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        // regenerate a failover task instance
        super.failoverTask(taskExecution);
    }

    @Override
    public TaskExecutionStatus matchState() {
        return TaskExecutionStatus.RUNNING_EXECUTION;
    }
}
