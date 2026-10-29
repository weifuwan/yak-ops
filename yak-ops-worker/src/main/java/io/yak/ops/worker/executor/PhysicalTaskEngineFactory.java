package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.TaskEngine;
import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBusCoordinator;
import io.yak.ops.worker.task.executor.listener.TaskExecutorLifecycleEventListener;

/** Assembles the same engine/repository/container/event-coordinator structure as DolphinScheduler. */
public final class PhysicalTaskEngineFactory {
    public TaskEngine createTaskEngine(PhysicalTaskExecutorRepository repository,
                                       PhysicalTaskExecutorContainerProvider container) {
        TaskExecutorLifecycleEventListener listener =
                new TaskExecutorLifecycleEventListener(repository, container);
        TaskExecutorEventBusCoordinator events = new TaskExecutorEventBusCoordinator(repository, listener);
        return new TaskEngine(repository, container, events);
    }
}
