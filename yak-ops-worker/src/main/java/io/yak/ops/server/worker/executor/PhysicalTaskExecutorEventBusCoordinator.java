package io.yak.ops.server.worker.executor;

import io.yak.ops.task.executor.eventbus.TaskExecutorEventBusCoordinator;

import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskExecutorEventBusCoordinator extends TaskExecutorEventBusCoordinator {

    public PhysicalTaskExecutorEventBusCoordinator(final PhysicalTaskExecutorRepository physicalTaskExecutorRepository,
                                                   final PhysicalTaskExecutorLifecycleEventListener physicalTaskExecutorLifecycleEventListener) {
        super("PhysicalTaskExecutorEventBusCoordinator", physicalTaskExecutorRepository);
        registerTaskExecutorLifecycleEventListener(physicalTaskExecutorLifecycleEventListener);
    }
}
