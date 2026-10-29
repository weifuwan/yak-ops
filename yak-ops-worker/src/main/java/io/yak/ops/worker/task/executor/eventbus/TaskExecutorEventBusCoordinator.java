package io.yak.ops.worker.task.executor.eventbus;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorRepository;
import io.yak.ops.worker.task.executor.events.AbstractTaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorFinalizeLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorKillLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorPauseLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorSuccessLifecycleEvent;
import io.yak.ops.worker.task.executor.listener.ITaskExecutorLifecycleEventListener;
import io.yak.ops.worker.task.executor.log.TaskExecutorMDCUtils;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DolphinScheduler lifecycle coordinator, retaining typed dispatch and task-local event serialization. */
public class TaskExecutorEventBusCoordinator implements ITaskExecutorEventBusCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(TaskExecutorEventBusCoordinator.class);
    private static final long DEFAULT_FIRE_INTERVAL = 50;
    private final String coordinatorName;
    private final ITaskExecutorRepository taskExecutorRepository;
    private final List<ITaskExecutorLifecycleEventListener> taskExecutorLifecycleEventListeners =
            new CopyOnWriteArrayList<>();
    private final Set<String> firingTaskExecutorIds = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService mainExecutorThreadPool;
    private ExecutorService workerExecutorThreadPool;

    public TaskExecutorEventBusCoordinator(String coordinatorName, ITaskExecutorRepository repository) {
        this.coordinatorName = coordinatorName;
        this.taskExecutorRepository = repository;
    }

    @Override
    public synchronized void start() {
        if (mainExecutorThreadPool != null) return;
        int size = Math.max(1, Runtime.getRuntime().availableProcessors());
        workerExecutorThreadPool = Executors.newFixedThreadPool(size);
        mainExecutorThreadPool = Executors.newSingleThreadScheduledExecutor();
        mainExecutorThreadPool.scheduleWithFixedDelay(
                this::fireTaskExecutorEventBus, 0, DEFAULT_FIRE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @Override
    public void registerTaskExecutorLifecycleEventListener(ITaskExecutorLifecycleEventListener listener) {
        taskExecutorLifecycleEventListeners.add(java.util.Objects.requireNonNull(listener));
    }

    private void fireTaskExecutorEventBus() {
        for (ITaskExecutor executor : taskExecutorRepository.getAll()) {
            String taskExecutorId = executor.getId();
            if (!firingTaskExecutorIds.add(taskExecutorId)) continue;
            try {
                workerExecutorThreadPool.execute(() -> {
                    try {
                        doFireTaskExecutorEventBus(executor);
                    } finally {
                        firingTaskExecutorIds.remove(taskExecutorId);
                    }
                });
            } catch (RuntimeException failure) {
                firingTaskExecutorIds.remove(taskExecutorId);
                LOG.error("Failed to schedule TaskExecutor event dispatch, taskId={}", taskExecutorId, failure);
            }
        }
    }

    private void doFireTaskExecutorEventBus(ITaskExecutor executor) {
        try (TaskExecutorMDCUtils.MDCAutoClosable ignored = TaskExecutorMDCUtils.logWithMDC(executor)) {
            AbstractTaskExecutorLifecycleEvent event =
                    executor.getTaskExecutorEventBus().poll().orElse(null);
            if (event == null) return;
            try {
                for (ITaskExecutorLifecycleEventListener listener : taskExecutorLifecycleEventListeners) {
                    switch (event.getType()) {
                        case DISPATCHED:
                            listener.onTaskExecutorDispatchedLifecycleEvent(
                                    (TaskExecutorDispatchedLifecycleEvent) event);
                            break;
                        case RUNNING:
                            listener.onTaskExecutorStartedLifecycleEvent((TaskExecutorStartedLifecycleEvent) event);
                            break;
                        case RUNTIME_CONTEXT_CHANGE:
                            listener.onTaskExecutorRuntimeContextChangedEvent(
                                    (TaskExecutorRuntimeContextChangedLifecycleEvent) event);
                            break;
                        case PAUSE:
                            listener.onTaskExecutorPauseLifecycleEvent((TaskExecutorPauseLifecycleEvent) event);
                            break;
                        case PAUSED:
                            listener.onTaskExecutorPausedLifecycleEvent((TaskExecutorPausedLifecycleEvent) event);
                            break;
                        case KILL:
                            listener.onTaskExecutorKillLifecycleEvent((TaskExecutorKillLifecycleEvent) event);
                            break;
                        case KILLED:
                            listener.onTaskExecutorKilledLifecycleEvent((TaskExecutorKilledLifecycleEvent) event);
                            break;
                        case SUCCESS:
                            listener.onTaskExecutorSuccessLifecycleEvent((TaskExecutorSuccessLifecycleEvent) event);
                            break;
                        case FAILED:
                            listener.onTaskExecutorFailLifecycleEvent((TaskExecutorFailedLifecycleEvent) event);
                            break;
                        case FINALIZE:
                            listener.onTaskExecutorFinalizeLifecycleEvent((TaskExecutorFinalizeLifecycleEvent) event);
                            break;
                        default:
                            throw new IllegalStateException("Unexpected task executor event " + event.getType());
                    }
                }
            } catch (RuntimeException failure) {
                // Requeue the original delayed event; preserve its original event timestamp.
                executor.getTaskExecutorEventBus().publish(event);
                LOG.error("Unable to handle physical task lifecycle event {}", event.getType(), failure);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (mainExecutorThreadPool != null) mainExecutorThreadPool.shutdownNow();
        if (workerExecutorThreadPool != null) workerExecutorThreadPool.shutdownNow();
    }
}
