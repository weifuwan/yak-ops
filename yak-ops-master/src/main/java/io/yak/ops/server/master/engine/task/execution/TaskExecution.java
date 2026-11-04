package io.yak.ops.server.master.engine.task.execution;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.graph.IWorkflowExecutionGraph;
import io.yak.ops.server.master.engine.task.client.ITaskExecutorClient;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskKillLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskPauseLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskStartLifecycleEvent;
import io.yak.ops.server.master.runner.TaskExecutionContextFactory;

import javax.annotation.Nullable;

import org.springframework.context.ApplicationContext;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskExecution implements ITaskExecution {

    private final ApplicationContext applicationContext;

    @Getter
    private final IWorkflowExecutionGraph workflowExecutionGraph;
    @Getter
    private final WorkflowEventBus workflowEventBus;
    @Getter
    private final WorkflowDefinition workflowDefinition;
    @Getter
    private final Project project;
    @Getter
    private final WorkflowInstance workflowInstance;
    @Getter
    private @Nullable TaskInstance taskInstance;
    @Getter
    private final TaskDefinition taskDefinition;
    @Getter
    private TaskExecutionContext taskExecutionContext;

    public TaskExecution(TaskExecutionBuilder taskExecutionBuilder) {
        this.applicationContext = taskExecutionBuilder.getApplicationContext();
        this.workflowExecutionGraph = checkNotNull(taskExecutionBuilder.getWorkflowExecutionGraph());
        this.workflowEventBus = checkNotNull(taskExecutionBuilder.getWorkflowEventBus());
        this.workflowDefinition = checkNotNull(taskExecutionBuilder.getWorkflowDefinition());
        this.project = checkNotNull(taskExecutionBuilder.getProject());
        this.workflowInstance = checkNotNull(taskExecutionBuilder.getWorkflowInstance());
        this.taskDefinition = checkNotNull(taskExecutionBuilder.getTaskDefinition());
        this.taskInstance = taskExecutionBuilder.getTaskInstance();
    }

    @Override
    public boolean isTaskInstanceInitialized() {
        return taskInstance != null;
    }

    @Override
    public void initializeFirstRunTaskInstance() {
        checkState(!isTaskInstanceInitialized(),
                "The task instance is already initialized, can't initialize first run task.");
        this.taskInstance = applicationContext.getBean(TaskInstanceFactories.class)
                .firstRunTaskInstanceFactory()
                .builder()
                .withTaskDefinition(taskDefinition)
                .withWorkflowInstance(workflowInstance)
                .build();
    }

    @Override
    public boolean isTaskInstanceCanRetry() {
        return taskInstance.getRetryTimes() < taskInstance.getMaxRetryTimes();
    }

    @Override
    public boolean isFailure() {
        return isTaskInstanceInitialized() && !isTaskInstanceCanRetry() && taskInstance.getState().isFailure();
    }

    @Override
    public void retry() {
        checkState(isTaskInstanceInitialized(), "The task instance is not initialized, can't initialize retry task.");
        this.taskInstance = applicationContext.getBean(TaskInstanceFactories.class)
                .retryTaskInstanceFactory()
                .builder()
                .withTaskInstance(taskInstance)
                .build();
        getWorkflowEventBus().publish(TaskStartLifecycleEvent.of(this));
    }

    @Override
    public void failover() {
        checkState(isTaskInstanceInitialized(), "The task instance is not initialized, can't failover.");
        if (takeOverTaskFromExecutor()) {
            log.info("Failover task success, the task {} has been taken-over from executor", taskInstance.getName());
            return;
        }
        this.taskInstance = applicationContext.getBean(TaskInstanceFactories.class)
                .failoverTaskInstanceFactory()
                .builder()
                .withTaskInstance(taskInstance)
                .build();
        getWorkflowEventBus().publish(TaskStartLifecycleEvent.of(this));
    }

    @Override
    public void pause() {
        getWorkflowEventBus().publish(TaskPauseLifecycleEvent.of(this));
    }

    @Override
    public void kill() {
        getWorkflowEventBus().publish(TaskKillLifecycleEvent.of(this));
    }

    @Override
    public void initializeTaskExecutionContext() {
        checkState(isTaskInstanceInitialized(),
                "The task instance is not initialized, can't initialize TaskExecutionContext.");
        this.taskExecutionContext = createTaskExecutionContext();
    }

    private TaskExecutionContext createTaskExecutionContext() {
        final TaskExecutionContextCreateRequest request = TaskExecutionContextCreateRequest.builder()
                .workflowExecutionGraph(workflowExecutionGraph)
                .workflowDefinition(workflowDefinition)
                .project(project)
                .workflowInstance(workflowInstance)
                .taskDefinition(taskDefinition)
                .taskInstance(taskInstance)
                .build();
        return applicationContext.getBean(TaskExecutionContextFactory.class).createTaskExecutionContext(request);
    }

    private boolean takeOverTaskFromExecutor() {
        checkState(isTaskInstanceInitialized(), "The task instance is null, can't take over from executor.");
        try {
            return applicationContext.getBean(ITaskExecutorClient.class).reassignWorkflowInstanceHost(this);
        } catch (Exception ex) {
            log.warn("Take over task: {} failed", taskInstance.getName(), ex);
            return false;
        }
    }

    @Override
    public int compareTo(ITaskExecution other) {
        if (other == null) {
            return 1;
        }
        int workflowInstancePriorityCompareResult = workflowInstance.getWorkflowInstancePriority().getCode() -
                other.getWorkflowInstance().getWorkflowInstancePriority().getCode();
        if (workflowInstancePriorityCompareResult != 0) {
            return workflowInstancePriorityCompareResult;
        }

        // smaller number, higher priority
        int taskInstancePriorityCompareResult = taskInstance.getTaskInstancePriority().getCode()
                - other.getTaskInstance().getTaskInstancePriority().getCode();
        if (taskInstancePriorityCompareResult != 0) {
            return taskInstancePriorityCompareResult;
        }

        // larger number, higher priority
        int taskGroupPriorityCompareResult =
                taskDefinition.getTaskGroupPriority() - other.getTaskDefinition().getTaskGroupPriority();
        if (taskGroupPriorityCompareResult != 0) {
            return -taskGroupPriorityCompareResult;
        }
        // earlier submit time, higher priority
        return taskInstance.getFirstSubmitTime().compareTo(other.getTaskInstance().getFirstSubmitTime());
    }

    @Override
    public String toString() {
        if (taskInstance != null) {
            return "TaskExecution{" + "name=" + getName() + ", state=" + taskInstance.getState() + '}';
        }
        return "TaskExecution{" + "name=" + getName() + '}';
    }
}
