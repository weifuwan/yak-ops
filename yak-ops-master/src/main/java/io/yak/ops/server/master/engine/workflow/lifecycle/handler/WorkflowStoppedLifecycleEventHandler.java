package io.yak.ops.server.master.engine.workflow.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowStoppedLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.statemachine.IWorkflowStateAction;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowStoppedLifecycleEventHandler
        extends
            AbstractWorkflowLifecycleEventHandler<WorkflowStoppedLifecycleEvent> {

    @Override
    public void handle(final IWorkflowStateAction workflowStateAction,
                       final IWorkflowExecution workflowExecution,
                       final WorkflowStoppedLifecycleEvent workflowStoppedEvent) {
        workflowStateAction.onStoppedEvent(workflowExecution, workflowStoppedEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return WorkflowLifecycleEventType.STOPPED;
    }
}
