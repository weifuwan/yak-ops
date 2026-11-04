package io.yak.ops.server.master.engine.workflow.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowFinalizeLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.statemachine.IWorkflowStateAction;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * This event handler will used to clear the WorkflowExecuteRunnable instance
 */
@Slf4j
@Component
public class WorkflowFinalizeLifecycleEventHandler
        extends
            AbstractWorkflowLifecycleEventHandler<WorkflowFinalizeLifecycleEvent> {

    @Override
    public void handle(final IWorkflowStateAction workflowStateAction,
                       final IWorkflowExecution workflowExecution,
                       final WorkflowFinalizeLifecycleEvent workflowFinalizeEvent) {
        workflowStateAction.onFinalizeEvent(workflowExecution, workflowFinalizeEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return WorkflowLifecycleEventType.FINALIZE;
    }
}
