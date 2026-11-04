package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFatalLifecycleEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.springframework.stereotype.Component;

@Component
public class TaskFatalLifecycleEventHandler extends AbstractTaskLifecycleEventHandler<TaskFatalLifecycleEvent> {

    @Override
    public void handle(final ITaskStateAction taskStateAction,
                       final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskFatalLifecycleEvent taskFatalEvent) {
        taskStateAction.onFatalEvent(workflowExecution, taskExecution, taskFatalEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return TaskLifecycleEventType.FATAL;
    }
}
