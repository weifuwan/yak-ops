package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskKillLifecycleEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskKillLifecycleEventHandler extends AbstractTaskLifecycleEventHandler<TaskKillLifecycleEvent> {

    @Override
    public void handle(final ITaskStateAction taskStateAction,
                       final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskKillLifecycleEvent taskKillEvent) {
        taskStateAction.onKillEvent(workflowExecution, taskExecution, taskKillEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return TaskLifecycleEventType.KILL;
    }
}
