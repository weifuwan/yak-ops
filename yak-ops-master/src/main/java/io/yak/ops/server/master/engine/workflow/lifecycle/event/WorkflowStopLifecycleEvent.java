package io.yak.ops.server.master.engine.workflow.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowStopLifecycleEvent extends AbstractWorkflowLifecycleLifecycleEvent {

    private final IWorkflowExecution workflowExecution;

    public static WorkflowStopLifecycleEvent of(final IWorkflowExecution workflowExecution) {
        return new WorkflowStopLifecycleEvent(workflowExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return WorkflowLifecycleEventType.STOP;
    }

    @Override
    public String toString() {
        return "WorkflowStopLifecycleEvent{" +
                "workflow=" + workflowExecution.getName() +
                '}';
    }
}
