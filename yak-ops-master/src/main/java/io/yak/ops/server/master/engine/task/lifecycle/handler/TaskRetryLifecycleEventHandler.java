package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRetryLifecycleEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskRetryLifecycleEventHandler extends AbstractTaskLifecycleEventHandler<TaskRetryLifecycleEvent> {

    @Override
    public void handle(final ITaskStateAction taskStateAction,
                       final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskRetryLifecycleEvent taskRetryLifecycleEvent) {
        taskStateAction.onRetryEvent(workflowExecution, taskExecution, taskRetryLifecycleEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return TaskLifecycleEventType.RETRY;
    }
}
