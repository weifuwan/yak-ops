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

package io.yak.ops.worker.executor;

import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * DolphinScheduler PhysicalTaskPluginFactory: resolve the TaskChannel by task type.
 * The deferred preparation callback bridges Yak Ops's durable Master claim, so
 * the Worker does not depend on business persistence.
 */
public final class PhysicalTaskPluginFactory {
    private final TaskPluginRegistry taskPluginRegistry;
    private final BiFunction<TaskExecutionContext, Supplier<ExecutableTaskPlugin>, PhysicalTask> taskPreparation;

    public PhysicalTaskPluginFactory(
            TaskPluginRegistry taskPluginRegistry,
            BiFunction<TaskExecutionContext, Supplier<ExecutableTaskPlugin>, PhysicalTask> taskPreparation) {
        this.taskPluginRegistry = Objects.requireNonNull(taskPluginRegistry);
        this.taskPreparation = Objects.requireNonNull(taskPreparation);
    }

    public PhysicalTask createPhysicalTask(final PhysicalTaskExecutor physicalTaskExecutor) {
        return taskPreparation.apply(
                physicalTaskExecutor.getTaskExecutionContext(), () -> getTaskChannel(physicalTaskExecutor));
    }

    public ExecutableTaskPlugin getTaskChannel(final PhysicalTaskExecutor physicalTaskExecutor) {
        final String taskType = physicalTaskExecutor.getTaskExecutionContext().getTaskType();
        final TaskPlugin plugin = taskPluginRegistry.get(taskType);
        return plugin instanceof ExecutableTaskPlugin ? (ExecutableTaskPlugin) plugin : null;
    }
}
