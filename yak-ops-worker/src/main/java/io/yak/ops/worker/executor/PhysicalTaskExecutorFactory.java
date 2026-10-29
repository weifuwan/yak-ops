package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorFactory;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import java.util.Objects;

/** The physical plugin factory is passed to the executor, not stored in its data context. */
public final class PhysicalTaskExecutorFactory implements ITaskExecutorFactory {
    private final PhysicalTaskPluginFactory physicalTaskPluginFactory;

    public PhysicalTaskExecutorFactory(PhysicalTaskPluginFactory physicalTaskPluginFactory) {
        this.physicalTaskPluginFactory = Objects.requireNonNull(physicalTaskPluginFactory);
    }

    @Override
    public ITaskExecutor createTaskExecutor(TaskExecutionContext context) {
        return new PhysicalTaskExecutor(context, physicalTaskPluginFactory);
    }
}
