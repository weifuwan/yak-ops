/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.worker.task.executor.worker;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.log.TaskExecutorMDCUtils;
import io.yak.ops.worker.task.executor.log.TaskExecutorMDCUtils.MDCAutoClosable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskExecutorWorker extends AbstractTaskExecutorWorker {
    private static final Logger LOG = LoggerFactory.getLogger(TaskExecutorWorker.class);

    private final Map<String, ITaskExecutor> registeredTaskExecutors = new ConcurrentHashMap<>();

    private final Map<String, ITaskExecutor> activeTaskExecutors = new ConcurrentHashMap<>();

    private final Lock activeTaskExecutorsChangeLock = new ReentrantLock();

    private final Condition activeTaskExecutorEmptyCondition = activeTaskExecutorsChangeLock.newCondition();

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
        while (!Thread.currentThread().isInterrupted()) {
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
                        // A failed observation does not prove an asynchronously submitted
                        // engine job has terminated. Keep the slot until confirmed.
                        LOG.error("Task executor state could not be confirmed, taskId={}", taskExecutor.getId(), e);
                        if (e instanceof VirtualMachineError fatal) {
                            throw fatal;
                        }
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
                LOG.info("TaskExecutorWorker(id={}) is interrupted", workerId, e);
                break;
            } finally {
                activeTaskExecutorsChangeLock.unlock();
            }
        }
    }

    @Override
    public void registerTaskExecutor(final ITaskExecutor taskExecutor) {
        final String taskExecutorId = taskExecutor.getId();
        if (registeredTaskExecutors.putIfAbsent(taskExecutorId, taskExecutor) != null) {
            throw new IllegalStateException("Task already registered: " + taskExecutorId);
        }
    }

    @Override
    public void unRegisterTaskExecutor(final ITaskExecutor taskExecutor) {
        final String taskExecutorId = taskExecutor.getId();
        if (!registeredTaskExecutors.containsKey(taskExecutorId)) {
            throw new IllegalStateException("The TaskExecutorWorker has not registered " + taskExecutor);
        }
        registeredTaskExecutors.remove(taskExecutorId);
    }

    @Override
    public void fireTaskExecutor(final ITaskExecutor taskExecutor) {
        activeTaskExecutorsChangeLock.lock();
        try {
            final String taskExecutorId = taskExecutor.getId();
            if (!registeredTaskExecutors.containsKey(taskExecutorId)) {
                throw new IllegalStateException("The TaskExecutorWorker has not registered " + taskExecutor);
            }
            if (activeTaskExecutors.containsKey(taskExecutorId)) {
                throw new IllegalStateException("The TaskExecutorWorker has already fired " + taskExecutor);
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
            final String taskExecutorId = taskExecutor.getId();
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
