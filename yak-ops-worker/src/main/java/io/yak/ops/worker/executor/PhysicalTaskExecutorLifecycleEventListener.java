package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.listener.TaskExecutorLifecycleEventListener;

/** DolphinScheduler PhysicalTaskExecutorLifecycleEventListener, wired to a local durable reporter. */
public final class PhysicalTaskExecutorLifecycleEventListener extends TaskExecutorLifecycleEventListener {
    public PhysicalTaskExecutorLifecycleEventListener(
            PhysicalTaskExecutorContainerProvider container,
            PhysicalTaskExecutorRepository repository,
            LocalTaskExecutorLifecycleEventReporter reporter) {
        super(container, repository, reporter);
    }
}
