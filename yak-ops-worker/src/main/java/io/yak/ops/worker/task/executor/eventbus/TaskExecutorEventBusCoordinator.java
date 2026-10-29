package io.yak.ops.worker.task.executor.eventbus;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorRepository;
import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.listener.TaskExecutorLifecycleEventListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Local counterpart of DolphinScheduler TaskExecutorEventBusCoordinator. */
public final class TaskExecutorEventBusCoordinator implements AutoCloseable {
    private final ITaskExecutorRepository repository;
    private final TaskExecutorLifecycleEventListener listener;
    private ScheduledExecutorService eventThread;
    public TaskExecutorEventBusCoordinator(ITaskExecutorRepository repository,
                                           TaskExecutorLifecycleEventListener listener) {
        this.repository = repository; this.listener = listener;
    }
    public synchronized void start() {
        if (eventThread != null) return;
        eventThread = Executors.newSingleThreadScheduledExecutor();
        eventThread.scheduleWithFixedDelay(this::drain, 0, 50, TimeUnit.MILLISECONDS);
    }
    private void drain() {
        for (ITaskExecutor executor : repository.getAll()) {
            TaskExecutorLifecycleEvent event;
            while ((event = executor.getTaskExecutorEventBus().poll().orElse(null)) != null) {
                try { listener.onEvent(executor, event); }
                catch (RuntimeException failure) {
                    executor.getTaskExecutorEventBus().publish(event);
                    break;
                }
            }
        }
    }
    @Override public synchronized void close() {
        if (eventThread != null) eventThread.shutdownNow();
    }
}
