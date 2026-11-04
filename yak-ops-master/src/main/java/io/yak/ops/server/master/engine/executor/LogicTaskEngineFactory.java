package io.yak.ops.server.master.engine.executor;

import io.yak.ops.task.executor.TaskEngine;
import io.yak.ops.task.executor.TaskEngineBuilder;

import org.springframework.stereotype.Component;

@Component
public class LogicTaskEngineFactory {

    private final LogicTaskExecutorRepository logicTaskExecutorRepository;

    private final LogicTaskExecutorContainerProvider logicTaskExecutorContainerDelegator;

    private final LogicTaskExecutorEventBusCoordinator logicTaskExecutorEventBusCoordinator;

    public LogicTaskEngineFactory(final LogicTaskExecutorRepository logicTaskExecutorRepository,
                                  final LogicTaskExecutorContainerProvider logicTaskExecutorContainerDelegator,
                                  final LogicTaskExecutorEventBusCoordinator logicTaskExecutorEventBusCoordinator) {
        this.logicTaskExecutorRepository = logicTaskExecutorRepository;
        this.logicTaskExecutorContainerDelegator = logicTaskExecutorContainerDelegator;
        this.logicTaskExecutorEventBusCoordinator = logicTaskExecutorEventBusCoordinator;
    }

    public TaskEngine createTaskEngine() {
        final TaskEngineBuilder taskEngineBuilder = TaskEngineBuilder.builder()
                .engineName("LogicTaskEngine")
                .taskExecutorRepository(logicTaskExecutorRepository)
                .taskExecutorContainerDelegator(logicTaskExecutorContainerDelegator)
                .taskExecutorEventBusCoordinator(logicTaskExecutorEventBusCoordinator)
                .build();
        return new TaskEngine(taskEngineBuilder);
    }

}
