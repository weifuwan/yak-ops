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
import io.yak.ops.worker.task.executor.TaskExecutorState;
import io.yak.ops.worker.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorSuccessLifecycleEvent;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTaskExecutorWorker implements ITaskExecutorWorker {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTaskExecutorWorker.class);

    protected void trackTaskExecutorState(final ITaskExecutor taskExecutor) {
        Objects.requireNonNull(taskExecutor, "taskExecutor is null");

        final TaskExecutorState taskExecutorState = taskExecutor.trackTaskExecutorState();

        switch (taskExecutorState) {
            case SUCCEEDED:
                onTaskExecutorSuccess(taskExecutor);
                break;
            case FAILED:
                onTaskExecutorFailed(taskExecutor);
                break;
            case KILLED:
                onTaskExecutorKilled(taskExecutor);
                break;
            case PAUSED:
                onTaskExecutorPaused(taskExecutor);
                break;
            case RUNNING:
                LOG.debug("TaskExecutor(id={}) is running", taskExecutor.getId());
                break;
            default:
                throw new IllegalStateException("Unexpected TaskExecutorState: " + taskExecutorState
                        + " for taskExecutor(id=" + taskExecutor.getId() + ")");
        }
    }

    protected void onTaskExecutorSuccess(final ITaskExecutor taskExecutor) {
        taskExecutor.getTaskExecutionContext().setEndTime(System.currentTimeMillis());
        taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorSuccessLifecycleEvent.of(taskExecutor));
        onTaskExecutorFinished(taskExecutor);
    }

    protected void onTaskExecutorKilled(final ITaskExecutor taskExecutor) {
        taskExecutor.getTaskExecutionContext().setEndTime(System.currentTimeMillis());
        taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorKilledLifecycleEvent.of(taskExecutor));
        onTaskExecutorFinished(taskExecutor);
    }

    protected void onTaskExecutorPaused(final ITaskExecutor taskExecutor) {
        taskExecutor.getTaskExecutionContext().setEndTime(System.currentTimeMillis());
        taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorPausedLifecycleEvent.of(taskExecutor));
        onTaskExecutorFinished(taskExecutor);
    }

    protected void onTaskExecutorFailed(final ITaskExecutor taskExecutor) {
        taskExecutor.getTaskExecutionContext().setEndTime(System.currentTimeMillis());
        taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorFailedLifecycleEvent.of(taskExecutor));
        onTaskExecutorFinished(taskExecutor);
    }

    protected void onTaskExecutorFinished(final ITaskExecutor taskExecutor) {
        unFireTaskExecutor(taskExecutor);
    }
}
