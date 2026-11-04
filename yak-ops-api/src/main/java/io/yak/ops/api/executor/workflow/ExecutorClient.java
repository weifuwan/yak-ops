package io.yak.ops.api.executor.workflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExecutorClient {

    @Autowired
    private TriggerWorkflowExecutorDelegate triggerWorkflowExecutorDelegate;

    @Autowired
    private BackfillWorkflowExecutorDelegate backfillWorkflowExecutorDelegate;

    @Autowired
    private RepeatRunningWorkflowInstanceExecutorDelegate repeatRunningWorkflowInstanceExecutorDelegate;

    @Autowired
    private RecoverFailureTaskInstanceExecutorDelegate recoverFailureTaskInstanceExecutorDelegate;

    @Autowired
    private RecoverSuspendedWorkflowInstanceExecutorDelegate recoverSuspendedWorkflowInstanceExecutorDelegate;

    @Autowired
    private PauseWorkflowInstanceExecutorDelegate pauseWorkflowInstanceExecutorDelegate;
    @Autowired
    private StopWorkflowInstanceExecutorDelegate stopWorkflowInstanceExecutorDelegate;

    public TriggerWorkflowExecutorDelegate triggerWorkflowDefinition() {
        return triggerWorkflowExecutorDelegate;
    }

    public BackfillWorkflowExecutorDelegate backfillWorkflowDefinition() {
        return backfillWorkflowExecutorDelegate;
    }

    public RepeatRunningWorkflowInstanceExecutorDelegate.RepeatRunningWorkflowInstanceOperation repeatRunningWorkflowInstance() {
        return new RepeatRunningWorkflowInstanceExecutorDelegate.RepeatRunningWorkflowInstanceOperation(
                repeatRunningWorkflowInstanceExecutorDelegate);
    }

    public RecoverFailureTaskInstanceExecutorDelegate.RecoverFailureTaskInstanceOperation recoverFailureTaskInstance() {
        return new RecoverFailureTaskInstanceExecutorDelegate.RecoverFailureTaskInstanceOperation(
                recoverFailureTaskInstanceExecutorDelegate);
    }

    public RecoverSuspendedWorkflowInstanceExecutorDelegate.RecoverSuspendedWorkflowInstanceOperation recoverSuspendedWorkflowInstanceOperation() {
        return new RecoverSuspendedWorkflowInstanceExecutorDelegate.RecoverSuspendedWorkflowInstanceOperation(
                recoverSuspendedWorkflowInstanceExecutorDelegate);
    }

    public PauseWorkflowInstanceExecutorDelegate.PauseWorkflowInstanceOperation pauseWorkflowInstance() {
        return new PauseWorkflowInstanceExecutorDelegate.PauseWorkflowInstanceOperation(
                pauseWorkflowInstanceExecutorDelegate);
    }

    public StopWorkflowInstanceExecutorDelegate.StopWorkflowInstanceOperation stopWorkflowInstance() {
        return new StopWorkflowInstanceExecutorDelegate.StopWorkflowInstanceOperation(
                stopWorkflowInstanceExecutorDelegate);
    }

}
