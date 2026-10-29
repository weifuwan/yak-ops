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

package io.yak.ops.worker.task.executor;

import io.yak.ops.worker.task.executor.container.ITaskExecutorContainer;
import io.yak.ops.worker.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.worker.task.executor.eventbus.ITaskExecutorEventBusCoordinator;
import io.yak.ops.worker.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorKillLifecycleEvent;
import io.yak.ops.worker.task.executor.log.TaskExecutorMDCUtils;
import java.util.Collection;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DolphinScheduler TaskEngine dispatch and lifecycle, adapted to String task IDs.
 * Synchronized duplicate admission and rollback protect durable Yak Ops submissions.
 */
public class TaskEngine implements ITaskEngine {
    private static final Logger LOG = LoggerFactory.getLogger(TaskEngine.class);

    private final String engineName;
    private final ITaskExecutorRepository taskExecutorRepository;
    private final ITaskExecutorContainerProvider<?> taskExecutorContainerDelegator;
    private final ITaskExecutorEventBusCoordinator taskExecutorEventBusCoordinator;

    public TaskEngine(final TaskEngineBuilder taskEngineBuilder) {
        engineName = taskEngineBuilder.getEngineName();
        taskExecutorContainerDelegator = Objects.requireNonNull(taskEngineBuilder.getTaskExecutorContainerDelegator());
        taskExecutorRepository = Objects.requireNonNull(taskEngineBuilder.getTaskExecutorRepository());
        taskExecutorEventBusCoordinator =
                Objects.requireNonNull(taskEngineBuilder.getTaskExecutorEventBusCoordinator());
    }

    @Override
    public void start() {
        taskExecutorEventBusCoordinator.start();
        LOG.info("{} started", engineName);
    }

    @Override
    public synchronized void submitTask(final ITaskExecutor taskExecutor) {
        try (final TaskExecutorMDCUtils.MDCAutoClosable ignored = TaskExecutorMDCUtils.logWithMDC(taskExecutor)) {
            if (taskExecutorRepository.contains(taskExecutor.getId())) {
                throw new IllegalArgumentException("Task executor already submitted: " + taskExecutor.getId());
            }
            final ITaskExecutorContainer executorContainer = taskExecutorContainerDelegator.getExecutorContainer();
            boolean dispatched = false;
            boolean registered = false;
            try {
                executorContainer.dispatch(taskExecutor);
                dispatched = true;
                taskExecutorRepository.put(taskExecutor);
                registered = true;
                taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorDispatchedLifecycleEvent.of(taskExecutor));
                executorContainer.start(taskExecutor);
            } catch (RuntimeException failure) {
                if (registered) {
                    taskExecutorRepository.remove(taskExecutor.getId());
                }
                if (dispatched) {
                    try {
                        executorContainer.finalize(taskExecutor);
                    } catch (RuntimeException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }
    }

    @Override
    public void pauseTask(final String taskExecutorId) {
        // Yak Ops TaskHandle has no suspend/resume contract.
        throw new UnsupportedOperationException("Pause is not supported by the embedded Task SPI");
    }

    @Override
    public void killTask(final String taskExecutorId) {
        final ITaskExecutor taskExecutor = getTaskExecutor(taskExecutorId);
        try (final TaskExecutorMDCUtils.MDCAutoClosable ignored = TaskExecutorMDCUtils.logWithMDC(taskExecutor)) {
            taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorKillLifecycleEvent.of(taskExecutor));
        }
    }

    @Override
    public Collection<ITaskExecutor> queryTaskExecutors() {
        // Exposes executor handles to Yak Ops shutdown/observation; the distributed
        // DolphinScheduler DTO query is not needed in a single-JVM Worker.
        return taskExecutorRepository.getAll();
    }

    @Override
    public void close() {
        try (final ITaskExecutorEventBusCoordinator ignored = taskExecutorEventBusCoordinator) {
            LOG.info("{} closed", engineName);
        } finally {
            taskExecutorContainerDelegator.getExecutorContainer().close();
        }
    }

    private ITaskExecutor getTaskExecutor(final String taskId) {
        return taskExecutorRepository
                .get(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Task Executor: " + taskId));
    }
}
