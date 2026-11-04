package io.yak.ops.server.worker.executor;

import io.yak.ops.server.worker.config.WorkerConfig;
import io.yak.ops.task.executor.container.ExclusiveThreadTaskExecutorContainer;
import io.yak.ops.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.task.executor.container.TaskExecutorContainerConfig;

import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskExecutorContainerProvider
        implements
            ITaskExecutorContainerProvider<ExclusiveThreadTaskExecutorContainer> {

    private final ExclusiveThreadTaskExecutorContainer taskExecutorContainer;

    public PhysicalTaskExecutorContainerProvider(final WorkerConfig workerConfig) {
        final TaskExecutorContainerConfig containerConfig = TaskExecutorContainerConfig.builder()
                .containerName("exclusive-task-executor-container")
                .taskExecutorThreadPoolSize(workerConfig.getPhysicalTaskConfig().getTaskExecutorThreadSize())
                .build();
        this.taskExecutorContainer = new ExclusiveThreadTaskExecutorContainer(containerConfig);
    }

    @Override
    public ExclusiveThreadTaskExecutorContainer getExecutorContainer() {
        return taskExecutorContainer;
    }
}
