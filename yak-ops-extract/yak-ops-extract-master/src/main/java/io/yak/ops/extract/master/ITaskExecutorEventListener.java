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

package io.yak.ops.extract.master;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorSuccessLifecycleEvent;

@RpcService
public interface ITaskExecutorEventListener {

    @RpcMethod
    void onTaskExecutorDispatched(final TaskExecutorDispatchedLifecycleEvent taskExecutorDispatchedLifecycleEvent);

    @RpcMethod
    void onTaskExecutorRunning(final TaskExecutorStartedLifecycleEvent taskExecutorStartedLifecycleEvent);

    @RpcMethod
    void onTaskExecutorRuntimeContextChanged(final TaskExecutorRuntimeContextChangedLifecycleEvent taskExecutorRuntimeContextChangedLifecycleEventr);

    @RpcMethod
    void onTaskExecutorSuccess(final TaskExecutorSuccessLifecycleEvent taskExecutorSuccessLifecycleEvent);

    @RpcMethod
    void onTaskExecutorFailed(final TaskExecutorFailedLifecycleEvent taskExecutorFailedLifecycleEvent);

    @RpcMethod
    void onTaskExecutorKilled(final TaskExecutorKilledLifecycleEvent taskExecutorKilledLifecycleEvent);

    @RpcMethod
    void onTaskExecutorPaused(final TaskExecutorPausedLifecycleEvent taskExecutorPausedLifecycleEvent);

}
