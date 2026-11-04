package io.yak.ops.server.master.engine.workflow.lifecycle.handler;

import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowStartLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowTimeoutLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.statemachine.IWorkflowStateAction;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowStartLifecycleEventHandler
        extends
            AbstractWorkflowLifecycleEventHandler<WorkflowStartLifecycleEvent> {

    @Override
    public void handle(final IWorkflowStateAction workflowStateAction,
                       final IWorkflowExecution workflowExecution,
                       final WorkflowStartLifecycleEvent workflowStartEvent) {

        workflowTimeoutMonitor(workflowExecution);
        workflowStateAction.onStartEvent(workflowExecution, workflowStartEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return WorkflowLifecycleEventType.START;
    }

    private void workflowTimeoutMonitor(final IWorkflowExecution workflowExecution) {
        final WorkflowInstance workflowInstance = workflowExecution.getWorkflowInstance();
        if (workflowInstance.getTimeout() <= 0) {
            log.debug("The workflow {} timeout {} is not configured or invalid, skip timeout monitor.",
                    workflowInstance.getName(),
                    workflowInstance.getTimeout());
            return;
        }
        workflowExecution.getWorkflowEventBus()
                .publish(WorkflowTimeoutLifecycleEvent.of(workflowExecution));
    }
}
