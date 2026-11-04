package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFailoverLifecycleEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.springframework.stereotype.Component;

@Component
public class TaskFailoverLifecycleEventHandler extends AbstractTaskLifecycleEventHandler<TaskFailoverLifecycleEvent> {

    @Override
    public void handle(final ITaskStateAction taskStateAction,
                       final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskFailoverLifecycleEvent taskFailoverEvent) {
        taskStateAction.onFailoverEvent(workflowExecution, taskExecution, taskFailoverEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return TaskLifecycleEventType.FAILOVER;
    }
}
