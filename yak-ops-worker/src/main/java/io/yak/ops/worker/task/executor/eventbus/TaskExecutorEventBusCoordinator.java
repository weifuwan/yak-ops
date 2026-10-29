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

package io.yak.ops.worker.task.executor.eventbus;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorRepository;
import io.yak.ops.worker.task.executor.events.AbstractTaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.events.ITaskExecutorLifecycleEvent;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskExecutorEventBusCoordinator implements ITaskExecutorEventBusCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(TaskExecutorEventBusCoordinator.class);

    private final String coordinatorName;

    private final ITaskExecutorRepository taskExecutorRepository;

    private final List<ITaskExecutorLifecycleEventListener> taskExecutorLifecycleEventListeners;

    private static final int DEFAULT_WORKER_SIZE = Runtime.getRuntime().availableProcessors();

    private static final long DEFAULT_FIRE_INTERVAL = 50;

    private final Set<String> firingTaskExecutorIds = ConcurrentHashMap.newKeySet();

    private ScheduledExecutorService mainExecutorThreadPool;

    private ThreadPoolExecutor workerExecutorThreadPool;

    public TaskExecutorEventBusCoordinator(
            final String coordinatorName, final ITaskExecutorRepository taskExecutorRepository) {
        this.coordinatorName = coordinatorName;
        this.taskExecutorRepository = taskExecutorRepository;
        this.taskExecutorLifecycleEventListeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public synchronized void start() {
        if (mainExecutorThreadPool != null) {
            return;
        }
        final int size = Math.max(1, DEFAULT_WORKER_SIZE);
        workerExecutorThreadPool = new ThreadPoolExecutor(
                size,
                size,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                Thread.ofPlatform()
                        .daemon(true)
                        .name(coordinatorName + "-eventbus-coordinator-worker-", 0)
                        .factory());
        mainExecutorThreadPool = new ScheduledThreadPoolExecutor(
                1,
                Thread.ofPlatform()
                        .daemon(true)
                        .name(coordinatorName + "-eventbus-coordinator-main-", 0)
                        .factory());
        // DolphinScheduler's scheduled coordinator + fixed worker pool; the
        // worker pool is initialized before the scheduler to avoid startup races.
        mainExecutorThreadPool.scheduleWithFixedDelay(
                this::fireTaskExecutorEventBus, 0, DEFAULT_FIRE_INTERVAL, TimeUnit.MILLISECONDS);
        LOG.info("{} started, worker size: {}", coordinatorName, size);
    }

    @Override
    public void registerTaskExecutorLifecycleEventListener(
            final ITaskExecutorLifecycleEventListener taskExecutorLifecycleEventListener) {
        java.util.Objects.requireNonNull(taskExecutorLifecycleEventListener);
        taskExecutorLifecycleEventListeners.add(taskExecutorLifecycleEventListener);
    }

    @Override
    public synchronized void close() {
        if (mainExecutorThreadPool != null) {
            mainExecutorThreadPool.shutdownNow();
        }
        if (workerExecutorThreadPool != null) {
            workerExecutorThreadPool.shutdownNow();
        }
        LOG.info("{} closed", coordinatorName);
    }

    private void fireTaskExecutorEventBus() {
        try {
            final Collection<ITaskExecutor> taskExecutors = taskExecutorRepository.getAll();
            if (taskExecutors.isEmpty()) {
                return;
            }
            for (final ITaskExecutor taskExecutor : taskExecutors) {
                final String taskExecutorId = taskExecutor.getId();
                // Equivalent per-task firing guard to upstream, but check/add is
                // atomic; upstream's asynchronous add has a duplicate-dispatch race.
                if (!firingTaskExecutorIds.add(taskExecutorId)) {
                    continue;
                }
                try {
                    workerExecutorThreadPool.execute(() -> {
                        try {
                            doFireTaskExecutorEventBus(taskExecutor);
                        } finally {
                            firingTaskExecutorIds.remove(taskExecutorId);
                        }
                    });
                } catch (RuntimeException dispatchFailure) {
                    firingTaskExecutorIds.remove(taskExecutorId);
                    LOG.error("Fire TaskExecutorEventBus scheduling error, taskId={}", taskExecutorId, dispatchFailure);
                }
            }
        } catch (Throwable throwable) {
            LOG.error("Fire TaskExecutorEventBus error", throwable);
        }
    }

    private void doFireTaskExecutorEventBus(final ITaskExecutor taskExecutor) {
        try (final TaskExecutorMDCUtils.MDCAutoClosable ignored = TaskExecutorMDCUtils.logWithMDC(taskExecutor)) {
            final TaskExecutorEventBus taskExecutorEventBus = taskExecutor.getTaskExecutorEventBus();
            if (taskExecutorEventBus.isEmpty()) {
                return;
            }
            Optional<AbstractTaskExecutorLifecycleEvent> headEventOptional = taskExecutorEventBus.poll();
            if (!headEventOptional.isPresent()) {
                return;
            }
            final ITaskExecutorLifecycleEvent taskExecutorLifecycleEvent = headEventOptional.get();
            try {
                for (final ITaskExecutorLifecycleEventListener taskExecutorLifecycleEventListener :
                        taskExecutorLifecycleEventListeners) {
                    switch (taskExecutorLifecycleEvent.getType()) {
                        case DISPATCHED:
                            taskExecutorLifecycleEventListener.onTaskExecutorDispatchedLifecycleEvent(
                                    ((TaskExecutorDispatchedLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case RUNNING:
                            taskExecutorLifecycleEventListener.onTaskExecutorStartedLifecycleEvent(
                                    ((TaskExecutorStartedLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case RUNTIME_CONTEXT_CHANGE:
                            taskExecutorLifecycleEventListener.onTaskExecutorRuntimeContextChangedEvent(
                                    ((TaskExecutorRuntimeContextChangedLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case PAUSE:
                            taskExecutorLifecycleEventListener.onTaskExecutorPauseLifecycleEvent(
                                    ((TaskExecutorPauseLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case PAUSED:
                            taskExecutorLifecycleEventListener.onTaskExecutorPausedLifecycleEvent(
                                    ((TaskExecutorPausedLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case KILL:
                            taskExecutorLifecycleEventListener.onTaskExecutorKillLifecycleEvent(
                                    ((TaskExecutorKillLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case KILLED:
                            taskExecutorLifecycleEventListener.onTaskExecutorKilledLifecycleEvent(
                                    ((TaskExecutorKilledLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case SUCCESS:
                            taskExecutorLifecycleEventListener.onTaskExecutorSuccessLifecycleEvent(
                                    ((TaskExecutorSuccessLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case FAILED:
                            taskExecutorLifecycleEventListener.onTaskExecutorFailLifecycleEvent(
                                    ((TaskExecutorFailedLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        case FINALIZE:
                            taskExecutorLifecycleEventListener.onTaskExecutorFinalizeLifecycleEvent(
                                    ((TaskExecutorFinalizeLifecycleEvent) taskExecutorLifecycleEvent));
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unsupported TaskExecutorLifecycleEvent: " + taskExecutorLifecycleEvent);
                    }
                }
                LOG.debug(
                        "Success fire {}", taskExecutorLifecycleEvent.getClass().getSimpleName());
            } catch (Exception e) {
                // Do not drop lifecycle commands on transient listener failures.
                // The event is already polled; reschedule with delay, never spin
                // on an expired entry. This is the embedded local ACK adaptation.
                headEventOptional.get().retryAfter(1_000);
                taskExecutorEventBus.publish(headEventOptional.get());
                LOG.error("Fire TaskExecutorLifecycleEvent: {} error", taskExecutorLifecycleEvent, e);
            }
        }
    }
}
