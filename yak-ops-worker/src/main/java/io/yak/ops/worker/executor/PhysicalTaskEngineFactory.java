package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.TaskEngine;
import io.yak.ops.worker.task.executor.TaskEngineBuilder;

public final class PhysicalTaskEngineFactory {
    public TaskEngine createTaskEngine(
            PhysicalTaskExecutorRepository repository, PhysicalTaskExecutorContainerProvider container) {
        LocalTaskExecutorLifecycleEventReporter reporter = new LocalTaskExecutorLifecycleEventReporter(repository);
        PhysicalTaskExecutorLifecycleEventListener listener =
                new PhysicalTaskExecutorLifecycleEventListener(container, repository, reporter);
        PhysicalTaskExecutorEventBusCoordinator coordinator =
                new PhysicalTaskExecutorEventBusCoordinator(repository, listener);
        TaskEngineBuilder builder = new TaskEngineBuilder();
        builder.setEngineName("PhysicalTaskEngine");
        builder.setTaskExecutorRepository(repository);
        builder.setTaskExecutorContainerDelegator(container);
        builder.setTaskExecutorEventBusCoordinator(coordinator);
        return new TaskEngine(builder);
    }
}
