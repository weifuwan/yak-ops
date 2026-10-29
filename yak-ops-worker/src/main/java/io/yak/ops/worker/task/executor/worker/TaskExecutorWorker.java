package io.yak.ops.worker.task.executor.worker;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.TaskExecutorState;
import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEventType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Port of DolphinScheduler TaskExecutorWorker: registered slots outlive fired tasks. */
public final class TaskExecutorWorker implements ITaskExecutorWorker {
    private final int workerId;
    private final Map<String, ITaskExecutor> registeredTaskExecutors = new ConcurrentHashMap<>();
    private final Map<String, ITaskExecutor> activeTaskExecutors = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition wakeup = lock.newCondition();

    public TaskExecutorWorker(int workerId) { this.workerId = workerId; }
    @Override public int getId() { return workerId; }

    @Override public void start() {
        while (!Thread.currentThread().isInterrupted()) {
            long waitMillis = 100;
            for (ITaskExecutor executor : activeTaskExecutors.values()) {
                try {
                    if (!executor.isStarted()) executor.start();
                    long remaining = executor.getRemainingTrackDelay();
                    if (remaining <= 0) {
                        TaskExecutorState state = executor.trackTaskExecutorState();
                        if (state.isFinalState()) {
                            executor.getTaskExecutorEventBus().publish(
                                    TaskExecutorLifecycleEvent.of(executor, terminalEvent(state)));
                            unFireTaskExecutor(executor);
                        }
                    } else {
                        waitMillis = Math.min(waitMillis, remaining);
                    }
                } catch (Throwable failure) {
                    executor.getTaskExecutorEventBus().publish(
                            TaskExecutorLifecycleEvent.of(executor, TaskExecutorLifecycleEventType.FAILED));
                    unFireTaskExecutor(executor);
                }
            }
            lock.lock();
            try {
                if (activeTaskExecutors.isEmpty()) wakeup.await();
                else wakeup.await(Math.max(1, waitMillis), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } finally { lock.unlock(); }
        }
    }

    private TaskExecutorLifecycleEventType terminalEvent(TaskExecutorState state) {
        switch (state) {
            case SUCCEEDED: return TaskExecutorLifecycleEventType.SUCCESS;
            case KILLED: return TaskExecutorLifecycleEventType.KILLED;
            case PAUSED: return TaskExecutorLifecycleEventType.PAUSED;
            default: return TaskExecutorLifecycleEventType.FAILED;
        }
    }

    @Override public void registerTaskExecutor(ITaskExecutor executor) {
        if (registeredTaskExecutors.putIfAbsent(executor.getId(), executor) != null)
            throw new IllegalStateException("Duplicate task registration: " + executor.getId());
    }
    @Override public void unRegisterTaskExecutor(ITaskExecutor executor) {
        registeredTaskExecutors.remove(executor.getId(), executor);
    }
    @Override public void fireTaskExecutor(ITaskExecutor executor) {
        lock.lock();
        try {
            if (!registeredTaskExecutors.containsKey(executor.getId()))
                throw new IllegalStateException("Task was not registered");
            if (activeTaskExecutors.putIfAbsent(executor.getId(), executor) != null)
                throw new IllegalStateException("Task already started");
            wakeup.signalAll();
        } finally { lock.unlock(); }
    }
    @Override public void unFireTaskExecutor(ITaskExecutor executor) {
        activeTaskExecutors.remove(executor.getId(), executor);
    }
    @Override public int getRegisteredTaskExecutorSize() { return registeredTaskExecutors.size(); }
    @Override public int getFiredTaskExecutorSize() { return activeTaskExecutors.size(); }
}
