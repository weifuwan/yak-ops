package io.yak.ops.server.worker.executor;

import io.yak.ops.plugin.storage.api.StorageOperator;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.server.worker.config.WorkerConfig;
import io.yak.ops.task.executor.eventbus.TaskExecutorEventBus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class PhysicalTaskExecutorBuilder {

    private TaskExecutionContext taskExecutionContext;

    private WorkerConfig workerConfig;

    private StorageOperator storageOperator;

    @Builder.Default
    private TaskExecutorEventBus taskExecutorEventBus = new TaskExecutorEventBus();

    private PhysicalTaskPluginFactory physicalTaskPluginFactory;

}
