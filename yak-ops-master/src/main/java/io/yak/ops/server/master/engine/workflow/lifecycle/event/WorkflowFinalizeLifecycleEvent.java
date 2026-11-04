package io.yak.ops.server.master.engine.workflow.lifecycle.event;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.execution.WorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The workflow instance finalize event, used to remove the {@link WorkflowExecution} from the master, will
 * clear the workflow instance related resources in memory.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowFinalizeLifecycleEvent extends AbstractWorkflowLifecycleLifecycleEvent {

    private IWorkflowExecution workflowExecution;

    public static WorkflowFinalizeLifecycleEvent of(IWorkflowExecution workflowExecution) {
        checkNotNull(workflowExecution, "workflowExecution is null");
        return new WorkflowFinalizeLifecycleEvent(workflowExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return WorkflowLifecycleEventType.FINALIZE;
    }

    @Override
    public String toString() {
        return "WorkflowFinalizeLifecycleEvent{" +
                "workflow=" + workflowExecution.getName() +
                '}';
    }
}
