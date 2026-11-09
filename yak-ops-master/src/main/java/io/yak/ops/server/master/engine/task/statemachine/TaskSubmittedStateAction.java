package io.yak.ops.server.master.engine.task.statemachine;

import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.engine.task.dispatcher.WorkerGroupDispatcherCoordinator;
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
import io.yak.ops.server.master.exception.TaskExecutionContextCreateException;
import io.yak.ops.server.master.utils.ExceptionUtils;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The action to be taken when a task is in the SUBMITTED state.
 */
@Slf4j
@Component
public class TaskSubmittedStateAction extends AbstractTaskStateAction {

    @Autowired
    private WorkerGroupDispatcherCoordinator workerGroupDispatcherCoordinator;

    @Override
    public void onStartEvent(final IWorkflowExecution workflowExecution,
                             final ITaskExecution taskExecution,
                             final TaskStartLifecycleEvent taskStartEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);

        if (workflowExecution.isWorkflowReadyPause()) {
            workflowExecution.getWorkflowEventBus().publish(TaskPausedLifecycleEvent.of(taskExecution));
            return;
        }

        if (workflowExecution.isWorkflowReadyStop()) {
            workflowExecution.getWorkflowEventBus().publish(TaskKilledLifecycleEvent.of(taskExecution));
            return;
        }

        tryToDispatchTask(taskExecution);
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
        final TaskInstance taskInstance = taskExecution.getTaskInstance();
        long remainTimeMills = DateUtils.getRemainTime(
                taskInstance.getFirstSubmitTime(),
                taskInstance.getDelayTime() * 60L) * 1_000;
        if (remainTimeMills > 0) {
            persistTaskInstanceUpdate(taskInstance,
                    instance -> instance.setState(TaskExecutionStatus.DELAY_EXECUTION));
            log.info("Current taskInstance: {} is choose delay execution, delay time: {}/min, remainTime: {}/ms",
                    taskInstance.getName(),
                    taskInstance.getDelayTime(),
                    remainTimeMills);
        }

        try {
            taskExecution.initializeTaskExecutionContext();
        } catch (Exception ex) {
            if (ExceptionUtils.isDatabaseConnectedFailedException(ex)) {
                throw ex;
            }
            log.error("Failed to initialize task execution context, taskName: {}", taskInstance.getName(), ex);
            throw new TaskExecutionContextCreateException(ex.getMessage());
        }

        workerGroupDispatcherCoordinator.dispatchTask(taskExecution, remainTimeMills);
    }

    @Override
    public void onDispatchedEvent(final IWorkflowExecution workflowExecution,
                                  final ITaskExecution taskExecution,
                                  final TaskDispatchedLifecycleEvent taskDispatchedEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        super.onDispatchedEvent(workflowExecution, taskExecution, taskDispatchedEvent);
    }

    @Override
    public void onPauseEvent(final IWorkflowExecution workflowExecution,
                             final ITaskExecution taskExecution,
                             final TaskPauseLifecycleEvent taskPauseEvent) {
        throwExceptionIfStateIsNotMatch(taskExecution);
        if (workerGroupDispatcherCoordinator.removeTask(taskExecution)) {
            log.info("Success pause task: {} before dispatch", taskExecution.getName());
            taskExecution.getWorkflowEventBus().publish(TaskPausedLifecycleEvent.of(taskExecution));
            return;
        }
        if (taskExecution.getTaskInstance().getTaskGroupId() > 0
                && taskGroupCoordinator.releaseWaitingTaskGroupSlot(taskExecution.getTaskInstance())) {
            log.info("Success pause task: {} while waiting for TaskGroup slot", taskExecution.getName());
            taskExecution.getWorkflowEventBus().publish(TaskPausedLifecycleEvent.of(taskExecution));
            return;
        }
        log.info("The task[id={}] is submitted and already dispatched, cannot pause, will try to pause it after 5s",
                taskExecution.getId());
        taskExecution.getWorkflowEventBus()
                .publish(TaskPauseLifecycleEvent.of(taskExecution, TimeUnit.SECONDS.toMillis(5)));
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
        if (workerGroupDispatcherCoordinator.removeTask(taskExecution)) {
            log.info("Success kill task[id={}] before dispatch", taskExecution.getId());
            taskExecution.getWorkflowEventBus().publish(TaskKilledLifecycleEvent.of(taskExecution));
            return;
        }
        if (taskExecution.getTaskInstance().getTaskGroupId() > 0
                && taskGroupCoordinator.releaseWaitingTaskGroupSlot(taskExecution.getTaskInstance())) {
            log.info("Success kill task: {} while waiting for TaskGroup slot", taskExecution.getName());
            taskExecution.getWorkflowEventBus().publish(TaskKilledLifecycleEvent.of(taskExecution));
            return;
        }
        log.info("The task[id={}] is submitted and already dispatched, cannot kill, will kill it after 5s",
                taskExecution.getId());
        taskExecution.getWorkflowEventBus()
                .publish(TaskKillLifecycleEvent.of(taskExecution, TimeUnit.SECONDS.toMillis(5)));
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
        return TaskExecutionStatus.SUBMITTED_SUCCESS;
    }

}
