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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TaskExecutorRepository implements ITaskExecutorRepository {

    private final Map<String, ITaskExecutor> taskExecutorMap = new ConcurrentHashMap<>();

    @Override
    public void put(final ITaskExecutor taskExecutor) {
        java.util.Objects.requireNonNull(taskExecutor, "taskExecutor");
        if (taskExecutorMap.putIfAbsent(taskExecutor.getId(), taskExecutor) != null) {
            throw new IllegalStateException("Task executor is already registered: " + taskExecutor.getId());
        }
    }

    @Override
    public Optional<ITaskExecutor> get(final String taskExecutorId) {
        return Optional.ofNullable(taskExecutorMap.get(taskExecutorId));
    }

    @Override
    public Collection<ITaskExecutor> getAll() {
        return taskExecutorMap.values();
    }

    @Override
    public boolean contains(final String taskExecutorId) {
        return taskExecutorMap.containsKey(taskExecutorId);
    }

    @Override
    public void remove(final String taskExecutorId) {
        taskExecutorMap.remove(taskExecutorId);
    }

    @Override
    public void clear() {
        taskExecutorMap.clear();
    }
}
