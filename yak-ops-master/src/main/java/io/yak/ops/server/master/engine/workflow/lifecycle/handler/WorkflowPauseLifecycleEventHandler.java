package io.yak.ops.server.master.engine.workflow.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowPauseLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.statemachine.IWorkflowStateAction;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowPauseLifecycleEventHandler
        extends
            AbstractWorkflowLifecycleEventHandler<WorkflowPauseLifecycleEvent> {

    @Override
    public void handle(final IWorkflowStateAction workflowStateAction,
                       final IWorkflowExecution workflowExecution,
                       final WorkflowPauseLifecycleEvent pauseEvent) {

        workflowStateAction.onPauseEvent(workflowExecution, pauseEvent);
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return WorkflowLifecycleEventType.PAUSE;
    }

}
