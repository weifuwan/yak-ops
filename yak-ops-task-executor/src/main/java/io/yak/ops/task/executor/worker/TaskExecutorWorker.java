package io.yak.ops.task.executor.worker;

import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.log.TaskExecutorMDCUtils;
import io.yak.ops.task.executor.log.TaskExecutorMDCUtils.MDCAutoClosable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskExecutorWorker extends AbstractTaskExecutorWorker {

    private final Map<Integer, ITaskExecutor> registeredTaskExecutors = new ConcurrentHashMap<>();

    private final Map<Integer, ITaskExecutor> activeTaskExecutors = new ConcurrentHashMap<>();

    private final Lock activeTaskExecutorsChangeLock = new ReentrantLock();

    private final Condition activeTaskExecutorEmptyCondition = activeTaskExecutorsChangeLock.newCondition();

    @Getter
    private final int workerId;

    public TaskExecutorWorker(int workerId) {
        this.workerId = workerId;
    }

    @Override
    public int getId() {
        return workerId;
    }

    @Override
    public void start() {
        while (true) {
            long minNextTrackDelay = 100;
            for (final ITaskExecutor taskExecutor : activeTaskExecutors.values()) {
                try (final MDCAutoClosable closable = TaskExecutorMDCUtils.logWithMDC(taskExecutor)) {
                    try {
                        if (!taskExecutor.isStarted()) {
                            taskExecutor.start();
                        }

                        final long remainingTrackDelay = taskExecutor.getRemainingTrackDelay();
                        if (remainingTrackDelay > 0) {
                            minNextTrackDelay = Math.min(minNextTrackDelay, remainingTrackDelay);
                        } else {
                            trackTaskExecutorState(taskExecutor);
                        }
                    } catch (Throwable e) {
                        log.error("{} execute failed", taskExecutor, e);
                        onTaskExecutorFailed(taskExecutor);
                    }
                }
            }

            activeTaskExecutorsChangeLock.lock();
            try {
                if (activeTaskExecutors.isEmpty()) {
                    activeTaskExecutorEmptyCondition.await();
                } else {
                    activeTaskExecutorEmptyCondition.await(minNextTrackDelay, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                log.info("TaskExecutorWorker(id={}) is interrupted", workerId, e);
                break;
            } finally {
                activeTaskExecutorsChangeLock.unlock();
            }
        }
    }

    @Override
    public void registerTaskExecutor(final ITaskExecutor taskExecutor) {
        final Integer taskExecutorId = taskExecutor.getId();
        if (registeredTaskExecutors.containsKey(taskExecutorId)) {
            throw new IllegalStateException(
                    "The TaskExecutorWorker has already registered " + taskExecutor);
        }
        registeredTaskExecutors.put(taskExecutorId, taskExecutor);
    }

    @Override
    public void unRegisterTaskExecutor(final ITaskExecutor taskExecutor) {
        final Integer taskExecutorId = taskExecutor.getId();
        if (!registeredTaskExecutors.containsKey(taskExecutorId)) {
            throw new IllegalStateException(
                    "The TaskExecutorWorker has not registered " + taskExecutor);
        }
        registeredTaskExecutors.remove(taskExecutorId);
    }

    @Override
    public void fireTaskExecutor(final ITaskExecutor taskExecutor) {
        activeTaskExecutorsChangeLock.lock();
        try {
            final Integer taskExecutorId = taskExecutor.getId();
            if (!registeredTaskExecutors.containsKey(taskExecutorId)) {
                throw new IllegalStateException(
                        "The TaskExecutorWorker has not registered " + taskExecutor);
            }
            if (activeTaskExecutors.containsKey(taskExecutorId)) {
                throw new IllegalStateException(
                        "The TaskExecutorWorker has already fired " + taskExecutor);
            }
            activeTaskExecutors.put(taskExecutorId, taskExecutor);
            activeTaskExecutorEmptyCondition.signalAll();
        } finally {
            activeTaskExecutorsChangeLock.unlock();
        }
    }

    @Override
    public void unFireTaskExecutor(ITaskExecutor taskExecutor) {
        try {
            activeTaskExecutorsChangeLock.lock();
            final Integer taskExecutorId = taskExecutor.getId();
            activeTaskExecutors.remove(taskExecutorId);
        } finally {
            activeTaskExecutorsChangeLock.unlock();
        }
    }

    @Override
    public int getRegisteredTaskExecutorSize() {
        return registeredTaskExecutors.size();
    }

    @Override
    public int getFiredTaskExecutorSize() {
        return activeTaskExecutors.size();
    }

}
