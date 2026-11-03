/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.cdc.runtime.datastream;

import io.yak.ops.cdc.runtime.tasks.StreamTask;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Handle for one asynchronous local pipeline execution. */
public final class StreamExecutionHandle {
    private final String id;
    private final String jobName;
    private final CompletableFuture<Void> completion;
    private final StreamTask task;
    private final Thread thread;

    StreamExecutionHandle(
            String id, String jobName, CompletableFuture<Void> completion,
            StreamTask task, Thread thread) {
        this.id = Objects.requireNonNull(id, "id");
        this.jobName = Objects.requireNonNull(jobName, "jobName");
        this.completion = Objects.requireNonNull(completion, "completion");
        this.task = Objects.requireNonNull(task, "task");
        this.thread = Objects.requireNonNull(thread, "thread");
    }

    public String getId() { return id; }
    public String getJobName() { return jobName; }
    public CompletableFuture<Void> getCompletion() { return completion; }

    public void cancel() {
        if (completion.cancel(true)) {
            task.cancel();
            thread.interrupt();
        }
    }
}
