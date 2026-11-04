package io.yak.ops.server.master.engine.executor;

import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.task.executor.container.SharedThreadTaskExecutorContainer;
import io.yak.ops.task.executor.container.TaskExecutorContainerConfig;

import org.springframework.stereotype.Component;

@Component
public class LogicTaskExecutorContainerProvider
        implements
            ITaskExecutorContainerProvider<SharedThreadTaskExecutorContainer> {

    private final SharedThreadTaskExecutorContainer taskExecutorContainer;

    public LogicTaskExecutorContainerProvider(final MasterConfig masterConfig) {
        final TaskExecutorContainerConfig containerConfig = TaskExecutorContainerConfig.builder()
                .taskExecutorThreadPoolSize(masterConfig.getLogicTaskConfig().getTaskExecutorThreadCount())
                .containerName("shared-task-executor-container")
                .build();
        this.taskExecutorContainer = new SharedThreadTaskExecutorContainer(containerConfig);
    }

    @Override
    public SharedThreadTaskExecutorContainer getExecutorContainer() {
        return taskExecutorContainer;
    }
}
