package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.master.ITaskExecutorEventListener;
import io.yak.ops.plugin.task.api.utils.LogUtils;
import io.yak.ops.server.master.engine.IWorkflowRepository;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskDispatchedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFailedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskKilledLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskPausedLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRunningLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRuntimeContextChangedEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskSuccessLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.task.executor.events.IReportableTaskExecutorLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorSuccessLifecycleEvent;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TaskExecutorEventListenerImpl implements ITaskExecutorEventListener {

    @Autowired
    private IWorkflowRepository workflowRepository;

    @Override
    public void onTaskExecutorDispatched(final TaskExecutorDispatchedLifecycleEvent taskExecutorDispatchedLifecycleEvent) {
        LogUtils.setWorkflowInstanceIdMDC(taskExecutorDispatchedLifecycleEvent.getWorkflowInstanceId());
        try {
            final ITaskExecution taskExecution =
                    getTaskExecution(taskExecutorDispatchedLifecycleEvent);
            final TaskDispatchedLifecycleEvent taskDispatchedLifecycleEvent = TaskDispatchedLifecycleEvent.builder()
                    .taskExecution(taskExecution)
                    .executorHost(taskExecutorDispatchedLifecycleEvent.getTaskInstanceHost())
                    .build();

            taskExecution.getWorkflowEventBus().publish(taskDispatchedLifecycleEvent);
        } finally {
            LogUtils.removeWorkflowInstanceIdMDC();
        }
    }

    @Override
    public void onTaskExecutorRunning(final TaskExecutorStartedLifecycleEvent taskExecutorStartedLifecycleEvent) {
        LogUtils.setWorkflowInstanceIdMDC(taskExecutorStartedLifecycleEvent.getWorkflowInstanceId());
        try {
            final ITaskExecution taskExecution =
                    getTaskExecution(taskExecutorStartedLifecycleEvent);
            final TaskRunningLifecycleEvent taskRunningEvent = TaskRunningLifecycleEvent.builder()
                    .taskExecution(taskExecution)
                    .startTime(new Date(taskExecutorStartedLifecycleEvent.getStartTime()))
                    .logPath(taskExecutorStartedLifecycleEvent.getLogPath())
                    .build();

            taskExecution.getWorkflowEventBus().publish(taskRunningEvent);
        } finally {
            LogUtils.removeWorkflowInstanceIdMDC();
        }
    }

    @Override
    public void onTaskExecutorRuntimeContextChanged(final TaskExecutorRuntimeContextChangedLifecycleEvent taskExecutorRuntimeContextChangedLifecycleEventr) {
        LogUtils.setWorkflowInstanceIdMDC(taskExecutorRuntimeContextChangedLifecycleEventr.getWorkflowInstanceId());
        try {
            final ITaskExecution taskExecution =
                    getTaskExecution(taskExecutorRuntimeContextChangedLifecycleEventr);

            final TaskRuntimeContextChangedEvent taskRuntimeContextChangedEvent =
                    TaskRuntimeContextChangedEvent.builder()
                            .taskExecution(taskExecution)
                            .runtimeContext(taskExecutorRuntimeContextChangedLifecycleEventr.getAppIds())
                            .build();

            taskExecution.getWorkflowEventBus().publish(taskRuntimeContextChangedEvent);
        } finally {
            LogUtils.removeWorkflowInstanceIdMDC();
        }
    }

    @Override
    public void onTaskExecutorSuccess(final TaskExecutorSuccessLifecycleEvent taskExecutorSuccessLifecycleEvent) {
        LogUtils.setWorkflowInstanceIdMDC(taskExecutorSuccessLifecycleEvent.getWorkflowInstanceId());
        try {
            final ITaskExecution taskExecution =
                    getTaskExecution(taskExecutorSuccessLifecycleEvent);
            final TaskSuccessLifecycleEvent taskSuccessEvent = TaskSuccessLifecycleEvent.builder()
                    .taskExecution(taskExecution)
                    .endTime(new Date(taskExecutorSuccessLifecycleEvent.getEndTime()))
                    .varPool(taskExecutorSuccessLifecycleEvent.getVarPool())
                    .needAlert(taskExecutorSuccessLifecycleEvent.isNeedAlert())
                    .taskAlertInfo(taskExecutorSuccessLifecycleEvent.getTaskAlertInfo())
                    .build();
            taskExecution.getWorkflowEventBus().publish(taskSuccessEvent);
        } finally {
            LogUtils.removeWorkflowInstanceIdMDC();
        }
    }

    @Override
    public void onTaskExecutorFailed(final TaskExecutorFailedLifecycleEvent taskExecutorFailedLifecycleEvent) {
        LogUtils.setWorkflowInstanceIdMDC(taskExecutorFailedLifecycleEvent.getWorkflowInstanceId());
        try {
            final ITaskExecution taskExecution =
                    getTaskExecution(taskExecutorFailedLifecycleEvent);
            final TaskFailedLifecycleEvent taskFailedEvent = TaskFailedLifecycleEvent.builder()
                    .taskExecution(taskExecution)
                    .endTime(new Date(taskExecutorFailedLifecycleEvent.getEndTime()))
                    .build();
            taskExecution.getWorkflowEventBus().publish(taskFailedEvent);
        } finally {
            LogUtils.removeWorkflowInstanceIdMDC();
        }
    }

    @Override
    public void onTaskExecutorKilled(final TaskExecutorKilledLifecycleEvent taskExecutorKilledLifecycleEvent) {
        LogUtils.setWorkflowInstanceIdMDC(taskExecutorKilledLifecycleEvent.getWorkflowInstanceId());
        try {
            final ITaskExecution taskExecution =
                    getTaskExecution(taskExecutorKilledLifecycleEvent);
            final TaskKilledLifecycleEvent taskKilledEvent = TaskKilledLifecycleEvent.builder()
                    .taskExecution(taskExecution)
                    .endTime(new Date(taskExecutorKilledLifecycleEvent.getEndTime()))
                    .build();
            taskExecution.getWorkflowEventBus().publish(taskKilledEvent);
        } finally {
            LogUtils.removeWorkflowInstanceIdMDC();
        }
    }

    @Override
    public void onTaskExecutorPaused(final TaskExecutorPausedLifecycleEvent taskExecutorPausedLifecycleEvent) {
        LogUtils.setWorkflowInstanceIdMDC(taskExecutorPausedLifecycleEvent.getWorkflowInstanceId());
        try {
            final ITaskExecution taskExecution =
                    getTaskExecution(taskExecutorPausedLifecycleEvent);
            final TaskPausedLifecycleEvent taskPausedEvent = TaskPausedLifecycleEvent.of(taskExecution);
            taskExecution.getWorkflowEventBus().publish(taskPausedEvent);
        } finally {
            LogUtils.removeWorkflowInstanceIdMDC();
        }
    }

    private ITaskExecution getTaskExecution(final IReportableTaskExecutorLifecycleEvent reportableTaskExecutorLifecycleEvent) {
        final int workflowInstanceId = reportableTaskExecutorLifecycleEvent.getWorkflowInstanceId();
        final int taskInstanceId = reportableTaskExecutorLifecycleEvent.getTaskInstanceId();

        final IWorkflowExecution workflowExecution = workflowRepository.get(workflowInstanceId);
        if (workflowExecution == null) {
            throw new IllegalArgumentException("Cannot find the WorkflowExecuteRunnable: " + workflowInstanceId);
        }
        final ITaskExecution taskExecution = workflowExecution.getWorkflowExecuteContext()
                .getWorkflowExecutionGraph()
                .getTaskExecutionById(taskInstanceId);
        if (taskExecution == null) {
            throw new IllegalArgumentException("Cannot find the TaskExecuteRunnable: " + taskInstanceId);
        }
        return taskExecution;
    }

}
