package io.yak.ops.task.executor;

import io.yak.ops.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.task.executor.eventbus.ITaskExecutorEventBusCoordinator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEngineBuilder {

    private String engineName;

    private ITaskExecutorRepository taskExecutorRepository;

    private ITaskExecutorContainerProvider taskExecutorContainerDelegator;

    private ITaskExecutorEventBusCoordinator taskExecutorEventBusCoordinator;

}
