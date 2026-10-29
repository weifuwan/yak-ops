package io.yak.ops.worker.task.executor.listener;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorRepository;
import io.yak.ops.worker.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEventType;

/** Handles execution commands and finalizes only after observer completion. */
public class TaskExecutorLifecycleEventListener {
    private final ITaskExecutorRepository repository;
    private final ITaskExecutorContainerProvider<?> containers;
    public TaskExecutorLifecycleEventListener(ITaskExecutorRepository repository,
                                              ITaskExecutorContainerProvider<?> containers) {
        this.repository = repository; this.containers = containers;
    }
    public void onEvent(ITaskExecutor executor, TaskExecutorLifecycleEvent event) {
        TaskExecutorLifecycleEventType type = event.getType();
        if (type == TaskExecutorLifecycleEventType.KILL) {
            executor.kill();
        } else if (type == TaskExecutorLifecycleEventType.PAUSE) {
            executor.pause();
        } else if (type.isFinished()) {
            executor.getTaskExecutorEventBus().publish(
                    TaskExecutorLifecycleEvent.of(executor, TaskExecutorLifecycleEventType.FINALIZE));
        } else if (type == TaskExecutorLifecycleEventType.FINALIZE) {
            containers.getExecutorContainer().finalize(executor);
            repository.remove(executor.getId());
        }
    }
}
