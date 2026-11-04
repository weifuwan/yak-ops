package io.yak.ops.server.master.engine.workflow.statemachine;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowFailedLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowFinalizeLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowPauseLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowPausedLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowStartLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowStopLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowStoppedLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowSucceedLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowTopologyLogicalTransitionWithTaskFinishLifecycleEvent;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The workflow submitted state should transform to running state when handle command.
 */
@Slf4j
@Component
public class WorkflowSubmittedStateAction extends AbstractWorkflowStateAction {

    @Override
    public void onStartEvent(final IWorkflowExecution workflowExecution,
                             final WorkflowStartLifecycleEvent workflowStartEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowStartEvent);
    }

    @Override
    public void onTopologyLogicalTransitionEvent(
                                                 final IWorkflowExecution workflowExecution,
                                                 final WorkflowTopologyLogicalTransitionWithTaskFinishLifecycleEvent workflowTopologyLogicalTransitionWithTaskFinishEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowTopologyLogicalTransitionWithTaskFinishEvent);
    }

    @Override
    public void onPauseEvent(final IWorkflowExecution workflowExecution,
                             final WorkflowPauseLifecycleEvent workflowPauseEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowPauseEvent);
    }

    @Override
    public void onPausedEvent(final IWorkflowExecution workflowExecution,
                              final WorkflowPausedLifecycleEvent workflowPausedEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowPausedEvent);
    }

    @Override
    public void onStopEvent(final IWorkflowExecution workflowExecution,
                            final WorkflowStopLifecycleEvent workflowStopEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowStopEvent);
    }

    @Override
    public void onStoppedEvent(final IWorkflowExecution workflowExecution,
                               final WorkflowStoppedLifecycleEvent workflowStoppedEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowStoppedEvent);
    }

    @Override
    public void onSucceedEvent(final IWorkflowExecution workflowExecution,
                               final WorkflowSucceedLifecycleEvent workflowSucceedEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowSucceedEvent);
    }

    @Override
    public void onFailedEvent(final IWorkflowExecution workflowExecution,
                              final WorkflowFailedLifecycleEvent workflowFailedEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowFailedEvent);
    }

    @Override
    public void onFinalizeEvent(final IWorkflowExecution workflowExecution,
                                final WorkflowFinalizeLifecycleEvent workflowFinalizeEvent) {
        throwExceptionIfStateIsNotMatch(workflowExecution);
        logWarningIfCannotDoAction(workflowExecution, workflowFinalizeEvent);
    }

    @Override
    public WorkflowExecutionStatus matchState() {
        return WorkflowExecutionStatus.SUBMITTED_SUCCESS;
    }

    /**
     * The running state can only finish with success/failure.
     */
    @Override
    protected void emitWorkflowFinishedEventIfApplicable(IWorkflowExecution workflowExecution) {
        throw new IllegalStateException(
                "The workflow " + workflowExecution.getName() +
                        "is submitted, shouldn't emit workflow finished event");
    }
}
