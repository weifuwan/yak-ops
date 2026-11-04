package io.yak.ops.server.master.engine.task.execution;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.graph.IWorkflowExecutionGraph;
import io.yak.ops.server.master.engine.workflow.execution.WorkflowExecution;

/**
 * The interface represent a running TaskInstance which belongs to a {@link WorkflowExecution}.
 */
public interface ITaskExecution
        extends
            Comparable<ITaskExecution> {

    /**
     * Get the task instance id.
     * <p> Need to know the id might change since the task instance might be regenerated.
     */
    default int getId() {
        return getTaskInstance().getId();
    }

    default String getName() {
        return getTaskDefinition().getName();
    }

    /**
     * Whether the task instance is initialized.
     * <p> If the ITaskExecution is never triggered, it is not initialized.
     * <p> If the ITaskExecution is created by failover, recovered then it is initialized.
     */
    boolean isTaskInstanceInitialized();

    /**
     * Initialize the task instance with {@link FirstRunTaskInstanceFactory}
     */
    void initializeFirstRunTaskInstance();

    /**
     * Initialize {@link TaskExecutionContext}.
     * <p> The TaskExecutionContext should be initialized before dispatch stage.
     */
    void initializeTaskExecutionContext();

    /**
     * Whether the task instance is running.
     */
    boolean isTaskInstanceCanRetry();

    boolean isFailure();

    /**
     * Retry the TaskExecution.
     * <p> Will create retry task instance and start it.
     */
    void retry();

    /**
     * Failover the TaskExecution.
     * <p> The failover logic is judged by the task instance state.
     */
    void failover();

    /**
     * Pause the TaskExecution.
     */
    void pause();

    /**
     * Kill the TaskExecution.
     */
    void kill();

    WorkflowEventBus getWorkflowEventBus();

    IWorkflowExecutionGraph getWorkflowExecutionGraph();

    WorkflowInstance getWorkflowInstance();

    TaskInstance getTaskInstance();

    TaskDefinition getTaskDefinition();

    TaskExecutionContext getTaskExecutionContext();
}
