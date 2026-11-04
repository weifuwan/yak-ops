package io.yak.ops.server.master.engine.workflow.lifecycle.handler;

import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowTimeoutLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.statemachine.IWorkflowStateAction;
import io.yak.ops.service.alert.WorkflowAlertManager;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowTimeoutLifecycleEventHandler
        extends
            AbstractWorkflowLifecycleEventHandler<WorkflowTimeoutLifecycleEvent> {

    private final WorkflowAlertManager workflowAlertManager;

    public WorkflowTimeoutLifecycleEventHandler(final WorkflowAlertManager workflowAlertManager) {
        this.workflowAlertManager = workflowAlertManager;
    }

    @Override
    public void handle(final IWorkflowStateAction workflowStateAction,
                       final IWorkflowExecution workflowExecution,
                       final WorkflowTimeoutLifecycleEvent workflowTimeoutLifecycleEvent) {
        final WorkflowInstance workflowInstance = workflowExecution.getWorkflowInstance();
        final boolean shouldSendAlert = workflowInstance.getWarningGroupId() != null;

        if (shouldSendAlert) {
            doWorkflowTimeoutAlert(workflowExecution);
        } else {
            log.info("Skipped sending timeout alert for workflow {} because warningGroupId is null.",
                    workflowInstance.getName());
        }

    }

    @Override
    public ILifecycleEventType matchEventType() {
        return WorkflowLifecycleEventType.TIMEOUT;
    }

    private void doWorkflowTimeoutAlert(final IWorkflowExecution workflowExecution) {
        final WorkflowInstance workflowInstance = workflowExecution.getWorkflowInstance();
        workflowAlertManager.sendWorkflowTimeoutAlert(workflowInstance);
    }
}
