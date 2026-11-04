package io.yak.ops.server.worker.executor;

import io.yak.ops.task.executor.TaskEngine;
import io.yak.ops.task.executor.TaskEngineBuilder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskEngineFactory {

    @Autowired
    private PhysicalTaskExecutorRepository physicalTaskExecutorRepository;

    @Autowired
    private PhysicalTaskExecutorContainerProvider physicalTaskExecutorContainerDelegator;

    @Autowired
    private PhysicalTaskExecutorEventBusCoordinator physicalTaskExecutorEventBusCoordinator;

    public TaskEngine createTaskEngine() {
        final TaskEngineBuilder taskEngineBuilder = TaskEngineBuilder.builder()
                .engineName("PhysicalTaskEngine")
                .taskExecutorRepository(physicalTaskExecutorRepository)
                .taskExecutorContainerDelegator(physicalTaskExecutorContainerDelegator)
                .taskExecutorEventBusCoordinator(physicalTaskExecutorEventBusCoordinator)
                .build();
        return new TaskEngine(taskEngineBuilder);
    }

}
