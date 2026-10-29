package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.container.ExclusiveThreadTaskExecutorContainer;
import io.yak.ops.worker.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.worker.task.executor.container.TaskExecutorContainerConfig;

/** Supplies one exclusive-thread physical task container. */
public final class PhysicalTaskExecutorContainerProvider
        implements ITaskExecutorContainerProvider<ExclusiveThreadTaskExecutorContainer> {
    private final ExclusiveThreadTaskExecutorContainer container;

    public PhysicalTaskExecutorContainerProvider(int slots) {
        TaskExecutorContainerConfig config = TaskExecutorContainerConfig.builder()
                .containerName("physical-task-executor")
                .taskExecutorThreadPoolSize(slots)
                .build();
        container = new ExclusiveThreadTaskExecutorContainer(config);
    }

    @Override
    public ExclusiveThreadTaskExecutorContainer getExecutorContainer() {
        return container;
    }
}
