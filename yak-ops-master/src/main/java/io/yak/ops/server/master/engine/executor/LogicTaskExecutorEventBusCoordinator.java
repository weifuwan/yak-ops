package io.yak.ops.server.master.engine.executor;

import io.yak.ops.task.executor.eventbus.TaskExecutorEventBusCoordinator;

import org.springframework.stereotype.Component;

@Component
public class LogicTaskExecutorEventBusCoordinator extends TaskExecutorEventBusCoordinator {

    public LogicTaskExecutorEventBusCoordinator(final LogicTaskExecutorRepository logicTaskExecutorRepository,
                                                final LogicTaskExecutorLifecycleEventListener logicTaskExecutorLifecycleEventListener) {
        super("LogicTaskExecutorEventBusCoordinator", logicTaskExecutorRepository);
        registerTaskExecutorLifecycleEventListener(logicTaskExecutorLifecycleEventListener);
    }
}
