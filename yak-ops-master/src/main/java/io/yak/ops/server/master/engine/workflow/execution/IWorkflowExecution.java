package io.yak.ops.server.master.engine.workflow.execution;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.graph.IWorkflowExecutionGraph;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.workflow.listener.IWorkflowLifecycleListener;
import io.yak.ops.server.master.engine.workflow.policy.IWorkflowFailureStrategy;
import io.yak.ops.server.master.runner.IWorkflowExecuteContext;

import java.util.List;

public interface IWorkflowExecution {

    /**
     * Get the id of the WorkflowExecution.
     */
    default int getId() {
        return getWorkflowInstance().getId();
    }

    /**
     * Get the name of the WorkflowExecution.
     */
    default String getName() {
        return getWorkflowInstance().getName();
    }

    /**
     * Pause the WorkflowExecution.
     */
    void pause();

    /**
     * Whether the workflow is ready to pause.
     */
    default boolean isWorkflowReadyPause() {
        final WorkflowExecutionStatus workflowExecutionStatus = getWorkflowInstance().getState();
        return workflowExecutionStatus == WorkflowExecutionStatus.READY_PAUSE;
    }

    /**
     * Stop the WorkflowExecution.
     */
    void stop();

    /**
     * Whether the workflow is ready to stop.
     */
    default boolean isWorkflowReadyStop() {
        final WorkflowExecutionStatus workflowExecutionStatus = getWorkflowInstance().getState();
        return workflowExecutionStatus == WorkflowExecutionStatus.READY_STOP;
    }

    /**
     * Kill the active tasks of the WorkflowExecution.
     */
    default void killActiveTasks() {
        getWorkflowExecutionGraph().getActiveTaskExecution().forEach(ITaskExecution::kill);
    }

    /**
     * Get the WorkflowExecuteContext belongs to the WorkflowExecution.
     */
    IWorkflowExecuteContext getWorkflowExecuteContext();

    /**
     * Get the WorkflowInstance belongs to the WorkflowExecution.
     */
    default WorkflowInstance getWorkflowInstance() {
        return getWorkflowExecuteContext().getWorkflowInstance();
    }

    /**
     * Get the state of the WorkflowExecution.
     */
    default WorkflowExecutionStatus getState() {
        return getWorkflowInstance().getState();
    }

    /**
     * Get the WorkflowEventBus belongs to the Workflow instance.
     */
    default WorkflowEventBus getWorkflowEventBus() {
        return getWorkflowExecuteContext().getWorkflowEventBus();
    }

    /**
     * Get the WorkflowExecutionGraph belongs to the Workflow instance.
     */
    default IWorkflowExecutionGraph getWorkflowExecutionGraph() {
        return getWorkflowExecuteContext().getWorkflowExecutionGraph();
    }

    /**
     * Get the WorkflowInstanceLifecycleListeners belongs to the Workflow instance.
     */
    List<IWorkflowLifecycleListener> getWorkflowLifecycleListeners();

    /**
     * Register a WorkflowInstanceLifecycleListener to the Workflow instance.
     */
    void registerWorkflowInstanceLifecycleListener(IWorkflowLifecycleListener listener);

    IWorkflowFailureStrategy getWorkflowFailureStrategy();
}
