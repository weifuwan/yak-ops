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
public class WorkflowStartLifecycleEvent extends AbstractWorkflowLifecycleLifecycleEvent {

    private IWorkflowExecution workflowExecution;

    public static WorkflowStartLifecycleEvent of(IWorkflowExecution workflowExecution) {
        return new WorkflowStartLifecycleEvent(workflowExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return WorkflowLifecycleEventType.START;
    }

    @Override
    public String toString() {
        return "WorkflowStartLifecycleEvent{" +
                "workflow=" + workflowExecution.getWorkflowExecuteContext().getWorkflowInstance().getName() +
                '}';
    }
}
