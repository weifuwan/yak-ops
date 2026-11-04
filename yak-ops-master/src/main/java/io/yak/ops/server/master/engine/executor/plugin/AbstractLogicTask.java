package io.yak.ops.server.master.engine.executor.plugin;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractLogicTask<T extends AbstractParameters> implements ILogicTask<T> {

    protected final TaskExecutionContext taskExecutionContext;

    protected final T taskParameters;

    protected TaskExecutionStatus taskExecutionStatus;

    public AbstractLogicTask(final TaskExecutionContext taskExecutionContext) {
        this.taskExecutionContext = taskExecutionContext;
        this.taskParameters = getTaskParameterDeserializer().deserialize(taskExecutionContext.getTaskParams());
        Preconditions.checkNotNull(taskParameters,
                "Deserialize task parameters: " + taskExecutionContext.getTaskParams());
        log.info("Success initialize parameters: \n{}", JSONUtils.toPrettyJsonString(taskParameters));
    }

    @Override
    public TaskExecutionStatus getTaskExecutionState() {
        return taskExecutionStatus;
    }

    protected boolean isRunning() {
        return taskExecutionStatus == TaskExecutionStatus.RUNNING_EXECUTION;
    }

    protected void onTaskRunning() {
        taskExecutionStatus = TaskExecutionStatus.RUNNING_EXECUTION;
    }

    protected void onTaskSuccess() {
        taskExecutionStatus = TaskExecutionStatus.SUCCESS;
    }

    protected void onTaskFailed() {
        taskExecutionStatus = TaskExecutionStatus.FAILURE;
    }

    protected void onTaskKilled() {
        taskExecutionStatus = TaskExecutionStatus.KILL;
    }

    protected void onTaskPaused() {
        taskExecutionStatus = TaskExecutionStatus.PAUSE;
    }
}
