package io.yak.ops.task.executor;

import io.yak.ops.plugin.task.api.TaskExecutionContext;

public interface ITaskExecutorFactory {

    ITaskExecutor createTaskExecutor(final TaskExecutionContext taskExecutionContext);
}
