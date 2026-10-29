package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBusCoordinator;

/** DolphinScheduler physical-task event coordinator specialization. */
public final class PhysicalTaskExecutorEventBusCoordinator extends TaskExecutorEventBusCoordinator {
    public PhysicalTaskExecutorEventBusCoordinator(
            PhysicalTaskExecutorRepository repository, PhysicalTaskExecutorLifecycleEventListener listener) {
        super("PhysicalTaskExecutorEventBusCoordinator", repository);
        registerTaskExecutorLifecycleEventListener(listener);
    }
}
