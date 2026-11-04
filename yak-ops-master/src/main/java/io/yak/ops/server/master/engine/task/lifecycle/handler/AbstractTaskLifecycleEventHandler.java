package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.engine.ILifecycleEventHandler;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.task.statemachine.TaskStateActionFactory;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTaskLifecycleEventHandler<T extends AbstractTaskLifecycleEvent>
        implements
            ILifecycleEventHandler<T> {

    @Autowired
    protected TaskStateActionFactory taskStateActionFactory;

    @Override
    public void handle(final IWorkflowExecution workflowExecution,
                       final T event) {
        final ITaskExecution taskExecution = event.getTaskExecution();
        final TaskExecutionStatus state = taskExecution.getTaskInstance().getState();
        final ITaskStateAction taskStateAction = taskStateActionFactory.getTaskStateAction(state);
        handle(taskStateAction, workflowExecution, taskExecution, event);
        log.info("Fired task {} {} with state {}",
                taskExecution.getName(),
                event,
                state.name());
    }

    public abstract void handle(final ITaskStateAction taskStateAction,
                                final IWorkflowExecution workflowExecution,
                                final ITaskExecution taskExecution,
                                final T event);

}
