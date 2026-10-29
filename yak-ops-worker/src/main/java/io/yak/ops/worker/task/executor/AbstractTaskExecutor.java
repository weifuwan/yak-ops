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

import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBus;
import io.yak.ops.worker.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.worker.task.executor.exceptions.TaskExecutorRuntimeException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DolphinScheduler AbstractTaskExecutor lifecycle; async completion is handled by PhysicalTaskExecutor. */
public abstract class AbstractTaskExecutor implements ITaskExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTaskExecutor.class);
    private static final long DEFAULT_TRACK_INTERVAL = 10_000;

    private volatile long latestStateTrackTime;
    private final AtomicBoolean startFlag = new AtomicBoolean();
    protected final AtomicReference<TaskExecutorState> taskExecutorState = new AtomicReference<>();
    protected final TaskExecutionContext taskExecutionContext;
    protected final TaskExecutorEventBus taskExecutorEventBus;

    public AbstractTaskExecutor(
            final TaskExecutionContext taskExecutionContext, final TaskExecutorEventBus taskExecutorEventBus) {
        this.taskExecutionContext = Objects.requireNonNull(taskExecutionContext);
        this.taskExecutorEventBus = Objects.requireNonNull(taskExecutorEventBus);
        this.latestStateTrackTime = 0;
        transitTaskExecutorState(TaskExecutorState.INITIALIZED);
    }

    @Override
    public void start() {
        if (!startFlag.compareAndSet(false, true)) {
            throw new TaskExecutorRuntimeException(this + " has already started");
        }
        initializeTaskContext();
        publishTaskRunningEvent();
        initializeTaskPlugin();
        // Yak Ops has no DolphinScheduler dryRun option; debug is not dryRun.
        doTriggerTaskPlugin();
    }

    @Override
    public boolean isStarted() {
        return startFlag.get();
    }

    @Override
    public TaskExecutorState trackTaskExecutorState() {
        final TaskExecutorState state = taskExecutorState.get();
        if (state.isFinalState()) {
            return state;
        }
        latestStateTrackTime = System.currentTimeMillis();
        transitTaskExecutorState(doTrackTaskPluginStatus());
        return taskExecutorState.get();
    }

    @Override
    public long getRemainingTrackDelay() {
        // Yak Ops TaskHandle signals an asynchronously confirmed terminal state;
        // do not delay the final ACK until the upstream 10-second polling interval.
        if (taskExecutorState.get().isFinalState()) {
            return 0;
        }
        if (latestStateTrackTime == 0) {
            return 0;
        }
        return latestStateTrackTime + DEFAULT_TRACK_INTERVAL - System.currentTimeMillis();
    }

    @Override
    public TaskExecutorEventBus getTaskExecutorEventBus() {
        return taskExecutorEventBus;
    }

    @Override
    public TaskExecutionContext getTaskExecutionContext() {
        return taskExecutionContext;
    }

    protected void transitTaskExecutorState(final TaskExecutorState state) {
        taskExecutorState.set(state);
    }

    protected abstract TaskExecutorState doTrackTaskPluginStatus();

    protected abstract void initializeTaskPlugin();

    protected abstract void doTriggerTaskPlugin();

    protected void initializeTaskContext() {
        taskExecutionContext.setStartTime(System.currentTimeMillis());
        LOG.debug(
                "Initialized taskContext id={}, taskType={}",
                taskExecutionContext.getTaskInstanceId(),
                taskExecutionContext.getTaskType());
    }

    private void publishTaskRunningEvent() {
        transitTaskExecutorState(TaskExecutorState.RUNNING);
        taskExecutorEventBus.publish(TaskExecutorStartedLifecycleEvent.of(this));
    }
}
