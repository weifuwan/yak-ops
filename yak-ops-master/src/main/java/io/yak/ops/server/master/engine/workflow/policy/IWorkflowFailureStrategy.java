package io.yak.ops.server.master.engine.workflow.policy;

import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

/**
 * Used to deal with {@link io.yak.ops.common.enums.FailureStrategy} when task failure occurs
 */
public interface IWorkflowFailureStrategy {

    void onTaskFailure(IWorkflowExecution workflowExecution,
                       ITaskExecution taskExecution);

}
