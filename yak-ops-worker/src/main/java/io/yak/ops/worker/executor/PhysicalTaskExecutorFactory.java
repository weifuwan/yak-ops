package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorFactory;
import io.yak.ops.worker.task.executor.TaskExecutionContext;

/** DolphinScheduler PhysicalTaskExecutorFactory, bound to the existing plugin SPI. */
public final class PhysicalTaskExecutorFactory implements ITaskExecutorFactory {
    @Override
    public ITaskExecutor createTaskExecutor(TaskExecutionContext context) {
        return new PhysicalTaskExecutor(context, context.getPhysicalTaskPluginFactory());
    }
}
