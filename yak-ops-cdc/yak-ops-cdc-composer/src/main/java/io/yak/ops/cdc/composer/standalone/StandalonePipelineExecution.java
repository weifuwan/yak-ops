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
import java.util.Objects;

/**
 * Submit the pipeline already assembled in a standalone execution environment.
 *
 * <p>Mirrors the responsibility of FlinkPipelineExecution. The runtime, not this adapter,
 * owns the readers, writers, scheduling and cleanup.
 */
public final class StandalonePipelineExecution implements PipelineExecution {

    private final StandaloneExecutionEnvironment env;
    private final String jobName;

    public StandalonePipelineExecution(StandaloneExecutionEnvironment env, String jobName) {
        this.env = Objects.requireNonNull(env, "env");
        this.jobName = Objects.requireNonNull(jobName, "jobName");
    }

    @Override
    public ExecutionInfo execute() throws Exception {
        return Objects.requireNonNull(
                env.execute(jobName),
                "StandaloneExecutionEnvironment returned null execution information");
    }
}
