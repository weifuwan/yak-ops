package io.yak.ops.worker.task.executor;

public interface ITaskExecutorFactory {
    ITaskExecutor createTaskExecutor(TaskExecutionContext taskExecutionContext);
}
