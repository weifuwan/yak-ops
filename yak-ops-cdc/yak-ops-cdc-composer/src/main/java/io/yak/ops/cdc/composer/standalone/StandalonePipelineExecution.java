/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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
 * A composed pipeline that has not yet been connected to a standalone execution engine.
 * Failing explicitly prevents the CLI from reporting a successful but inactive CDC task.
 */
public final class StandalonePipelineExecution implements PipelineExecution {

    private final StandalonePipelinePlan plan;

    public StandalonePipelineExecution(StandalonePipelinePlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public StandalonePipelinePlan getPlan() {
        return plan;
    }

    @Override
    public ExecutionInfo execute() {
        throw new UnsupportedOperationException(
                "Standalone pipeline plan was composed, but the CDC Runtime has not yet been "
                        + "connected to SourceReader and SinkWriter. No task was started.");
    }
}
