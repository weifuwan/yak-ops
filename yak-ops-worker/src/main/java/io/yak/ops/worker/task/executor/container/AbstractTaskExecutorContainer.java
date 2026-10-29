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

package io.yak.ops.worker.task.executor.container;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.exceptions.TaskExecutorRuntimeException;
import io.yak.ops.worker.task.executor.worker.TaskExecutorWorker;
import io.yak.ops.worker.task.executor.worker.TaskExecutorWorkers;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTaskExecutorContainer implements ITaskExecutorContainer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTaskExecutorContainer.class);
    private volatile boolean closed;

    private final TaskExecutorAssignmentTable taskExecutorAssignmentTable;

    protected final ThreadPoolExecutor taskExecutorThreadPool;

    protected final TaskExecutorWorkers taskExecutorWorkers;

    public AbstractTaskExecutorContainer(final TaskExecutorContainerConfig containerConfig) {
        final int threadPoolSize = containerConfig.getTaskExecutorThreadPoolSize();
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Task executor slots must be positive");
        }
        this.taskExecutorThreadPool = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                Thread.ofPlatform()
                        .daemon(true)
                        .name(containerConfig.getContainerName() + "-worker-", 0)
                        .factory());
        this.taskExecutorWorkers = new TaskExecutorWorkers(threadPoolSize);
        this.taskExecutorAssignmentTable = new TaskExecutorAssignmentTable();
        startAllThreadTaskExecutorWorker();
    }

    @Override
    public void dispatch(final ITaskExecutor taskExecutor) {
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Task executor container is closed");
            }
            Optional<TaskExecutorWorker> taskExecutorWorkerCandidate = getTaskExecutorWorkerCandidate(taskExecutor);
            if (!taskExecutorWorkerCandidate.isPresent()) {
                LOG.info(
                        "All ExclusiveThreadTaskExecutorWorker are busy, cannot submit taskExecutor(id={})",
                        taskExecutor.getId());
                throw new TaskExecutorRuntimeException("All ExclusiveThreadTaskExecutorWorker are busy");
            }
            final TaskExecutorWorker taskExecutorWorker = taskExecutorWorkerCandidate.get();
            taskExecutorWorker.registerTaskExecutor(taskExecutor);
            try {
                taskExecutorAssignmentTable.registerTaskExecutor(taskExecutor, taskExecutorWorker);
            } catch (RuntimeException failedAssignment) {
                taskExecutorWorker.unRegisterTaskExecutor(taskExecutor);
                throw failedAssignment;
            }
        }
    }

    @Override
    public void start(final ITaskExecutor taskExecutor) {
        final Integer workerId = taskExecutorAssignmentTable.getTaskExecutorWorkerId(taskExecutor);
        if (workerId == null) {
            throw new IllegalStateException(
                    "The taskExecutor: " + taskExecutor.getId() + " is not registered to any worker");
        }
        final TaskExecutorWorker taskExecutorWorker = taskExecutorWorkers.getWorkerById(workerId);
        taskExecutorWorker.fireTaskExecutor(taskExecutor);
    }

    @Override
    public void pause(final ITaskExecutor taskExecutor) {
        taskExecutor.pause();
    }

    @Override
    public void kill(final ITaskExecutor taskExecutor) {
        taskExecutor.kill();
    }

    @Override
    public synchronized void finalize(final ITaskExecutor taskExecutor) {
        // Yak Ops has no remote-log uploader. A finalization event is emitted only
        // after confirmed durable terminal persistence by the local reporter.
        if (taskExecutorAssignmentTable.isTaskExecutorRegistered(taskExecutor)) {
            final Integer workerId = taskExecutorAssignmentTable.getTaskExecutorWorkerId(taskExecutor);
            final TaskExecutorWorker worker = taskExecutorWorkers.getWorkerById(workerId);
            worker.unFireTaskExecutor(taskExecutor);
            worker.unRegisterTaskExecutor(taskExecutor);
            taskExecutorAssignmentTable.unregisterTaskExecutor(taskExecutor);
        }
        taskExecutor.finalizeTask();
    }

    @Override
    public double slotUsage() {
        final List<TaskExecutorWorker> allWorkers = taskExecutorWorkers.getWorkers();
        final long activeWorkerCount = allWorkers.stream()
                .filter(taskExecutorWorker -> taskExecutorWorker.getRegisteredTaskExecutorSize() != 0)
                .count();
        return activeWorkerCount / (double) allWorkers.size();
    }

    public TaskExecutorAssignmentTable getTaskExecutorAssignmentTable() {
        return taskExecutorAssignmentTable;
    }

    public TaskExecutorWorkers getTaskExecutorWorkers() {
        return taskExecutorWorkers;
    }

    private void startAllThreadTaskExecutorWorker() {
        for (final TaskExecutorWorker taskExecutorWorker : taskExecutorWorkers.getWorkers()) {
            taskExecutorThreadPool.submit(taskExecutorWorker::start);
        }
    }

    /**
     * Get the candidate worker to execute the task executor.
     * <p> If the container is full, return empty.
     */
    protected abstract Optional<TaskExecutorWorker> getTaskExecutorWorkerCandidate(final ITaskExecutor taskExecutor);

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        taskExecutorThreadPool.shutdownNow();
    }
}
