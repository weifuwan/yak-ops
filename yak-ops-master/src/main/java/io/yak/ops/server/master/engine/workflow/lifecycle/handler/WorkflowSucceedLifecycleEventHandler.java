package io.yak.ops.server.master.engine.workflow.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowSucceedLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.statemachine.IWorkflowStateAction;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowSucceedLifecycleEventHandler
        extends
            AbstractWorkflowLifecycleEventHandler<WorkflowSucceedLifecycleEvent> {

    @Override
    public void handle(final IWorkflowStateAction workflowStateAction,
                       final IWorkflowExecution workflowExecution,
                       final WorkflowSucceedLifecycleEvent workflowSucceedEvent) {
        workflowStateAction.onSucceedEvent(workflowExecution, workflowSucceedEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return WorkflowLifecycleEventType.SUCCEED;
    }
}
