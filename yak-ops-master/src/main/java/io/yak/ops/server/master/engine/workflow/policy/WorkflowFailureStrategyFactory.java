package io.yak.ops.server.master.engine.workflow.policy;

import io.yak.ops.common.enums.FailureStrategy;

public class WorkflowFailureStrategyFactory {

    public static IWorkflowFailureStrategy getStrategy(final FailureStrategy failureStrategy) {
        if (failureStrategy == FailureStrategy.END) {
            return new EndWorkflowFailureStrategy();
        }
        return new ContinueWorkflowFailureStrategy();
    }

}
