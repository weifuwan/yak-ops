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

package io.yak.ops.cdc.composer.standalone;

import io.yak.ops.cdc.composer.PipelineExecution;
import io.yak.ops.cdc.runtime.datastream.StreamExecutionEnvironment;
import io.yak.ops.cdc.runtime.datastream.StreamExecutionHandle;
import java.util.Objects;

/** Submit a previously composed standalone graph to the single-JVM runtime. */
public final class StandalonePipelineExecution implements PipelineExecution {

    private final StreamExecutionEnvironment env;
    private final String jobName;
    private volatile StreamExecutionHandle handle;

    public StandalonePipelineExecution(StreamExecutionEnvironment env, String jobName) {
        this.env = Objects.requireNonNull(env, "env");
        this.jobName = Objects.requireNonNull(jobName, "jobName");
    }

    @Override
    public ExecutionInfo execute() {
        handle = env.executeAsync(jobName);
        return new ExecutionInfo(handle.getId(), jobName);
    }

    /** Exposes completion and cancellation after execute(). */
    public StreamExecutionHandle getHandle() {
        if (handle == null) {
            throw new IllegalStateException("Pipeline has not been submitted");
        }
        return handle;
    }
}
