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

/**
 * Represents the action to be taken when a workflow is in a certain state and receive a target event.
 * <p> Each {@link WorkflowExecutionStatus} should have a corresponding {@link IWorkflowStateAction} implementation.
 *
 * @see WorkflowSubmittedStateAction
 * @see WorkflowRunningStateAction
 * @see WorkflowReadyPauseStateAction
 * @see WorkflowPausedStateAction
 * @see WorkflowReadyStopStateAction
 * @see WorkflowStoppedStateAction
 * @see WorkflowSerialWaitStateAction
 * @see WorkflowFailedStateAction
 * @see WorkflowSuccessStateAction
 * @see WorkflowFailoverStateAction
 */
public interface IWorkflowStateAction {

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowStartLifecycleEvent}.
     */
    void onStartEvent(final IWorkflowExecution workflowExecution,
                      final WorkflowStartLifecycleEvent workflowStartEvent);

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowTopologyLogicalTransitionWithTaskFinishLifecycleEvent}.
     */
    void onTopologyLogicalTransitionEvent(final IWorkflowExecution workflowExecution,
                                          final WorkflowTopologyLogicalTransitionWithTaskFinishLifecycleEvent workflowTopologyLogicalTransitionWithTaskFinishEvent);

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowPauseLifecycleEvent}.
     */
    void onPauseEvent(final IWorkflowExecution workflowExecution,
                      final WorkflowPauseLifecycleEvent workflowPauseEvent);

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowPausedLifecycleEvent}.
     */
    void onPausedEvent(final IWorkflowExecution workflowExecution,
                       final WorkflowPausedLifecycleEvent workflowPausedEvent);

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowStopLifecycleEvent}.
     */
    void onStopEvent(final IWorkflowExecution workflowExecution,
                     final WorkflowStopLifecycleEvent workflowStopEvent);

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowStoppedLifecycleEvent}.
     */
    void onStoppedEvent(final IWorkflowExecution workflowExecution,
                        final WorkflowStoppedLifecycleEvent workflowStoppedEvent);

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowSucceedLifecycleEvent}.
     */
    void onSucceedEvent(final IWorkflowExecution workflowExecution,
                        final WorkflowSucceedLifecycleEvent workflowSucceedEvent);

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowFailedLifecycleEvent}.
     */
    void onFailedEvent(final IWorkflowExecution workflowExecution,
                       final WorkflowFailedLifecycleEvent workflowFailedEvent);

    /**
     * Perform the necessary actions when the workflow in a certain state receive a {@link WorkflowFinalizeLifecycleEvent}.
     */
    void onFinalizeEvent(final IWorkflowExecution workflowExecution,
                         final WorkflowFinalizeLifecycleEvent workflowFinalizeEvent);

    /**
     * Get the {@link WorkflowExecutionStatus} that this action match.
     */
    WorkflowExecutionStatus matchState();
}
