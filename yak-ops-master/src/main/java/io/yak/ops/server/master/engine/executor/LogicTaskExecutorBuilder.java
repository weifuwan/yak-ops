package io.yak.ops.server.master.engine.executor;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.server.master.engine.executor.plugin.LogicTaskPluginFactoryBuilder;
import io.yak.ops.task.executor.eventbus.TaskExecutorEventBus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class LogicTaskExecutorBuilder {

    private TaskExecutionContext taskExecutionContext;

    @Builder.Default
    private TaskExecutorEventBus taskExecutorEventBus = new TaskExecutorEventBus();

    private LogicTaskPluginFactoryBuilder logicTaskPluginFactoryBuilder;

}
