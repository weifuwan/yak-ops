package io.yak.ops.server.worker.executor;

import io.yak.ops.plugin.storage.api.StorageOperator;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.utils.LogUtils;
import io.yak.ops.server.worker.config.WorkerConfig;
import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.ITaskExecutorFactory;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PhysicalTaskExecutorFactory implements ITaskExecutorFactory {

    private final WorkerConfig workerConfig;

    private final PhysicalTaskPluginFactory physicalTaskPluginFactory;

    private final StorageOperator storageOperator;

    public PhysicalTaskExecutorFactory(final WorkerConfig workerConfig,
                                       final PhysicalTaskPluginFactory physicalTaskPluginFactory,
                                       final StorageOperator storageOperator) {
        this.workerConfig = workerConfig;
        this.physicalTaskPluginFactory = physicalTaskPluginFactory;
        this.storageOperator = storageOperator;
    }

    @Override
    public ITaskExecutor createTaskExecutor(final TaskExecutionContext taskExecutionContext) {
        assemblyTaskLogPath(taskExecutionContext);

        final PhysicalTaskExecutorBuilder physicalTaskExecutorBuilder = PhysicalTaskExecutorBuilder.builder()
                .taskExecutionContext(taskExecutionContext)
                .workerConfig(workerConfig)
                .storageOperator(storageOperator)
                .physicalTaskPluginFactory(physicalTaskPluginFactory)
                .build();
        return new PhysicalTaskExecutor(physicalTaskExecutorBuilder);
    }

    private void assemblyTaskLogPath(final TaskExecutionContext taskExecutionContext) {
        taskExecutionContext.setLogPath(LogUtils.getTaskInstanceLogFullPath(taskExecutionContext));
    }

}
