package io.yak.ops.server.master.engine.workflow.policy;

import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import lombok.extern.slf4j.Slf4j;

/**
 * The strategy used to deal with {@link io.yak.ops.common.enums.FailureStrategy#END} when task failure occurs.
 * <p> Will kill the active tasks and end the workflow execution.
 */
@Slf4j
public class EndWorkflowFailureStrategy implements IWorkflowFailureStrategy {

    @Override
    public void onTaskFailure(IWorkflowExecution workflowExecution,
                              ITaskExecution taskExecution) {
        log.info("The workflow instance: [{}] using END failure strategy, will kill the active tasks.",
                workflowExecution.getName());
        workflowExecution.killActiveTasks();
    }

}
