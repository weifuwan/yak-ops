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

package io.yak.ops.worker.task.executor.listener;

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

public interface ITaskExecutorLifecycleEventListener {

    void onTaskExecutorDispatchedLifecycleEvent(final TaskExecutorDispatchedLifecycleEvent event);

    void onTaskExecutorStartedLifecycleEvent(final TaskExecutorStartedLifecycleEvent event);

    void onTaskExecutorRuntimeContextChangedEvent(final TaskExecutorRuntimeContextChangedLifecycleEvent event);

    void onTaskExecutorPauseLifecycleEvent(final TaskExecutorPauseLifecycleEvent event);

    void onTaskExecutorPausedLifecycleEvent(final TaskExecutorPausedLifecycleEvent event);

    void onTaskExecutorKillLifecycleEvent(final TaskExecutorKillLifecycleEvent event);

    void onTaskExecutorKilledLifecycleEvent(final TaskExecutorKilledLifecycleEvent event);

    void onTaskExecutorSuccessLifecycleEvent(final TaskExecutorSuccessLifecycleEvent event);

    void onTaskExecutorFailLifecycleEvent(final TaskExecutorFailedLifecycleEvent event);

    void onTaskExecutorFinalizeLifecycleEvent(final TaskExecutorFinalizeLifecycleEvent event);
}
