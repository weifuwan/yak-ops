package io.yak.ops.worker.task.executor;

import io.yak.ops.worker.task.executor.container.ITaskExecutorContainer;
import io.yak.ops.worker.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBusCoordinator;
import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEventType;
import java.util.Collection;
import java.util.Objects;

/** DolphinScheduler TaskEngine dispatch order with rollback on partial admission failures. */
public final class TaskEngine implements ITaskEngine {
    private final ITaskExecutorRepository repository;
    private final ITaskExecutorContainerProvider<?> containerProvider;
    private final TaskExecutorEventBusCoordinator events;

    public TaskEngine(ITaskExecutorRepository repository,
                      ITaskExecutorContainerProvider<?> containerProvider,
                      TaskExecutorEventBusCoordinator events) {
        this.repository = Objects.requireNonNull(repository);
        this.containerProvider = Objects.requireNonNull(containerProvider);
        this.events = Objects.requireNonNull(events);
    }

    @Override public void start() { events.start(); }

    @Override
    public synchronized void submitTask(ITaskExecutor executor) {
        ITaskExecutorContainer container = containerProvider.getExecutorContainer();
        boolean dispatched = false;
        boolean registered = false;
        try {
            if (repository.contains(executor.getId())) {
                throw new IllegalStateException("Task already submitted: " + executor.getId());
            }
            container.dispatch(executor);
            dispatched = true;
            repository.put(executor);
            registered = true;
            executor.getTaskExecutorEventBus().publish(
                    TaskExecutorLifecycleEvent.of(executor, TaskExecutorLifecycleEventType.DISPATCHED));
            container.start(executor);
        } catch (RuntimeException failure) {
            if (registered) repository.remove(executor.getId());
            if (dispatched) container.finalize(executor);
            throw failure;
        }
    }

    @Override public void pauseTask(String id) {
        getTaskExecutor(id).getTaskExecutorEventBus().publish(
                TaskExecutorLifecycleEvent.of(getTaskExecutor(id), TaskExecutorLifecycleEventType.PAUSE));
    }

    @Override public void killTask(String id) {
        ITaskExecutor executor = getTaskExecutor(id);
        executor.getTaskExecutorEventBus().publish(
                TaskExecutorLifecycleEvent.of(executor, TaskExecutorLifecycleEventType.KILL));
    }

    @Override public Collection<ITaskExecutor> queryTaskExecutors() { return repository.getAll(); }
    private ITaskExecutor getTaskExecutor(String id) {
        return repository.get(id).orElseThrow(() -> new IllegalArgumentException("Unknown task executor: " + id));
    }
    @Override public void close() { events.close(); containerProvider.getExecutorContainer().close(); }
}
