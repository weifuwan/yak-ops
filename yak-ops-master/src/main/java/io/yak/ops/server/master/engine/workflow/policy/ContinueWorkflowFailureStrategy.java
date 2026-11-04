package io.yak.ops.server.master.engine.workflow.policy;

import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

/**
 * The strategy used to deal with {@link io.yak.ops.common.enums.FailureStrategy#CONTINUE} when task failure occurs.
 * <p> Will wait the active the tasks finished.
 */
public class ContinueWorkflowFailureStrategy implements IWorkflowFailureStrategy {

    @Override
    public void onTaskFailure(IWorkflowExecution workflowExecution,
                              ITaskExecution taskExecution) {
        // do nothing, just continue workflow execution
    }

}
