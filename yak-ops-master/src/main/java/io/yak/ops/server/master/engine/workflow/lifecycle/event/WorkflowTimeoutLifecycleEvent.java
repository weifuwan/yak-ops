package io.yak.ops.server.master.engine.workflow.lifecycle.event;

import static com.google.common.base.Preconditions.checkState;

import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;

import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowTimeoutLifecycleEvent extends AbstractWorkflowLifecycleLifecycleEvent {

    private IWorkflowExecution workflowExecution;

    protected WorkflowTimeoutLifecycleEvent(final IWorkflowExecution workflowExecution,
                                            final long timeout) {
        super(timeout);
        this.workflowExecution = workflowExecution;
    }

    public static WorkflowTimeoutLifecycleEvent of(IWorkflowExecution workflowExecution) {
        final WorkflowInstance workflowInstance = workflowExecution.getWorkflowInstance();
        checkState(workflowInstance != null,
                "The workflow instance must be initialized before creating workflow timeout event.");

        final int timeout = workflowInstance.getTimeout();
        checkState(timeout > 0, "The workflow timeout: %s must > 0 minutes", timeout);

        long delayTime = System.currentTimeMillis() - workflowInstance.getRestartTime().getTime()
                + TimeUnit.MINUTES.toMillis(timeout);
        return new WorkflowTimeoutLifecycleEvent(workflowExecution, delayTime);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return WorkflowLifecycleEventType.TIMEOUT;
    }

    @Override
    public String toString() {
        return "WorkflowTimeoutLifecycleEvent{" +
                "workflow=" + workflowExecution.getWorkflowExecuteContext().getWorkflowInstance().getName() +
                '}';
    }
}
