package io.yak.ops.server.master.engine.workflow.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The event that represent the workflow has failed.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowFailedLifecycleEvent extends AbstractWorkflowLifecycleLifecycleEvent {

    private final IWorkflowExecution workflowExecution;

    public static WorkflowFailedLifecycleEvent of(final IWorkflowExecution workflowExecution) {
        return new WorkflowFailedLifecycleEvent(workflowExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return WorkflowLifecycleEventType.FAILED;
    }

    @Override
    public String toString() {
        return "WorkflowFailedLifecycleEvent{" +
                "workflow=" + workflowExecution.getName() +
                '}';
    }
}
