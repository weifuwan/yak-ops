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

package io.yak.ops.plugin.task.grpc;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

public class GrpcTaskChannel implements TaskChannel {

    @Override
    public AbstractTask createTask(TaskExecutionContext taskRequest) {
        return new GrpcTask(taskRequest);
    }

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, GrpcParameters.class);
    }
}
