package io.yak.ops.server.master.engine.workflow.execution;

import static com.google.common.base.Preconditions.checkArgument;

import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowPauseLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowStopLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.listener.IWorkflowLifecycleListener;
import io.yak.ops.server.master.engine.workflow.policy.IWorkflowFailureStrategy;
import io.yak.ops.server.master.engine.workflow.policy.WorkflowFailureStrategyFactory;
import io.yak.ops.server.master.runner.IWorkflowExecuteContext;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowExecution implements IWorkflowExecution {

    @Getter
    private final IWorkflowExecuteContext workflowExecuteContext;

    @Getter
    private final List<IWorkflowLifecycleListener> workflowInstanceLifecycleListeners;

    @Getter
    private final IWorkflowFailureStrategy workflowFailureStrategy;

    public WorkflowExecution(WorkflowExecutionBuilder workflowExecutionBuilder) {
        this.workflowExecuteContext = workflowExecutionBuilder.getWorkflowExecuteContextBuilder().build();
        this.workflowInstanceLifecycleListeners =
                new ArrayList<>(workflowExecuteContext.getWorkflowInstanceLifecycleListeners());
        this.workflowFailureStrategy = WorkflowFailureStrategyFactory
                .getStrategy(workflowExecuteContext.getWorkflowInstance().getFailureStrategy());
    }

    @Override
    public void pause() {
        getWorkflowEventBus().publish(WorkflowPauseLifecycleEvent.of(this));
    }

    @Override
    public void stop() {
        getWorkflowEventBus().publish(WorkflowStopLifecycleEvent.of(this));
    }

    @Override
    public List<IWorkflowLifecycleListener> getWorkflowLifecycleListeners() {
        return workflowInstanceLifecycleListeners;
    }

    @Override
    public void registerWorkflowInstanceLifecycleListener(IWorkflowLifecycleListener listener) {
        checkArgument(listener != null, "listener cannot be null");
        workflowInstanceLifecycleListeners.add(listener);
    }

    @Override
    public String toString() {
        final WorkflowInstance workflowInstance = workflowExecuteContext.getWorkflowInstance();
        return "WorkflowExecution{" +
                "name=" + workflowInstance.getName() +
                ", state=" + workflowInstance.getState().name() +
                '}';
    }
}
