package io.yak.ops.server.master.engine.workflow.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;

import com.google.common.base.Preconditions;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowPausedLifecycleEvent extends AbstractWorkflowLifecycleLifecycleEvent {

    private final IWorkflowExecution workflowExecution;

    public static WorkflowPausedLifecycleEvent of(final IWorkflowExecution workflowExecution) {
        Preconditions.checkNotNull(workflowExecution, "workflowExecution is null");
        return new WorkflowPausedLifecycleEvent(workflowExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return WorkflowLifecycleEventType.PAUSED;
    }

    @Override
    public String toString() {
        return "WorkflowPausedLifecycleEvent{" +
                "workflow=" + workflowExecution.getName() +
                '}';
    }
}
