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

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorFactory;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import java.util.Objects;

/** DolphinScheduler PhysicalTaskExecutorFactory, with a local TaskHandle bridge. */
public final class PhysicalTaskExecutorFactory implements ITaskExecutorFactory {
    private final PhysicalTaskPluginFactory physicalTaskPluginFactory;

    public PhysicalTaskExecutorFactory(final PhysicalTaskPluginFactory physicalTaskPluginFactory) {
        this.physicalTaskPluginFactory = Objects.requireNonNull(physicalTaskPluginFactory);
    }

    @Override
    public ITaskExecutor createTaskExecutor(final TaskExecutionContext taskExecutionContext) {
        final PhysicalTaskExecutorBuilder physicalTaskExecutorBuilder = PhysicalTaskExecutorBuilder.builder()
                .taskExecutionContext(taskExecutionContext)
                .physicalTaskPluginFactory(physicalTaskPluginFactory)
                .build();
        return new PhysicalTaskExecutor(physicalTaskExecutorBuilder);
    }
}
