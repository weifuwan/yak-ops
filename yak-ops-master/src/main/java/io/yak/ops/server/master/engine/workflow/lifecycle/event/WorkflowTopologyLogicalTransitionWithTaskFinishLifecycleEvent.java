package io.yak.ops.server.master.engine.workflow.lifecycle.event;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;

import java.util.Optional;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The event of topology logical transition in workflow, right now the transaction is only used for the task finish and trigger the next step.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowTopologyLogicalTransitionWithTaskFinishLifecycleEvent
        extends
            AbstractWorkflowLifecycleLifecycleEvent {

    private final IWorkflowExecution workflowExecution;

    private final ITaskExecution taskExecution;

    public static WorkflowTopologyLogicalTransitionWithTaskFinishLifecycleEvent of(
                                                                                   final IWorkflowExecution workflowExecution,
                                                                                   final ITaskExecution taskExecution) {
        checkNotNull(workflowExecution, "workflowExecution is null");
        checkNotNull(taskExecution, "taskExecution is null");
        return new WorkflowTopologyLogicalTransitionWithTaskFinishLifecycleEvent(
                workflowExecution,
                taskExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return WorkflowLifecycleEventType.TOPOLOGY_LOGICAL_TRANSACTION_WITH_TASK_FINISH;
    }

    @Override
    public String toString() {
        return "WorkflowTopologyLogicalTransitionWithTaskFinishLifecycleEvent{"
                + "task=" + taskExecution.getName()
                + "taskState="
                + Optional.ofNullable(taskExecution.getTaskInstance()).map(TaskInstance::getState)
                        .map(Enum::name).orElse(null)
                + '}';
    }
}
