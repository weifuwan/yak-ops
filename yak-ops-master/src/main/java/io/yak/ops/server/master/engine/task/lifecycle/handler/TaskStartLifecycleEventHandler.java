package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.plugin.task.api.enums.TaskTimeoutStrategy;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskStartLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskTimeoutLifecycleEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskStartLifecycleEventHandler extends AbstractTaskLifecycleEventHandler<TaskStartLifecycleEvent> {

    @Autowired
    private MasterConfig masterConfig;

    @Override
    public void handle(final IWorkflowExecution workflowExecution,
                       final TaskStartLifecycleEvent taskStartLifecycleEvent) {
        final ITaskExecution taskExecution = taskStartLifecycleEvent.getTaskExecution();
        // Since if the ITaskExecution is start at the first time, then it might not be initialized.
        // So we need to initialize the task instance here.
        // Otherwise, we cannot find the statemachine by task instance state.
        if (!taskExecution.isTaskInstanceInitialized()) {
            taskExecution.initializeFirstRunTaskInstance();
        }
        taskTimeoutMonitor(taskExecution);
        super.handle(workflowExecution, taskStartLifecycleEvent);
    }

    @Override
    public void handle(final ITaskStateAction taskStateAction,
                       final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskStartLifecycleEvent event) {
        taskStateAction.onStartEvent(workflowExecution, taskExecution, event);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return TaskLifecycleEventType.START;
    }

    private void taskTimeoutMonitor(final ITaskExecution taskExecution) {
        final TaskDefinition taskDefinition = taskExecution.getTaskDefinition();
        int taskTimeout = taskDefinition.getTimeout();
        if (taskTimeout > 0 && taskDefinition.getTimeoutNotifyStrategy() != null) {
            log.debug("The task {} timeout {} is invalided, so the timeout monitor will not be started.",
                    taskDefinition.getName(),
                    taskDefinition.getTimeout());
            taskExecution.getWorkflowEventBus().publish(TaskTimeoutLifecycleEvent.of(
                    taskExecution, taskDefinition.getTimeoutNotifyStrategy(), taskTimeout));
        }

        int systemTimeout = (int) masterConfig.getServerLoadProtection().getMaxTaskInstanceRuntime().toMinutes();
        if (systemTimeout > 0) {
            taskExecution.getWorkflowEventBus().publish(TaskTimeoutLifecycleEvent.of(
                    taskExecution, TaskTimeoutStrategy.FAILED, systemTimeout));
        }
    }

}
