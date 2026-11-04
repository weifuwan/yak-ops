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

package io.yak.ops.extract.common;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.common.transportor.TaskInstanceLogFileDownloadRequest;
import io.yak.ops.extract.common.transportor.TaskInstanceLogFileDownloadResponse;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryRequest;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryResponse;

@RpcService
public interface ILogService {

    @RpcMethod
    TaskInstanceLogFileDownloadResponse getTaskInstanceWholeLogFileBytes(TaskInstanceLogFileDownloadRequest taskInstanceLogFileDownloadRequest);

    @RpcMethod
    TaskInstanceLogPageQueryResponse pageQueryTaskInstanceLog(TaskInstanceLogPageQueryRequest taskInstanceLogPageQueryRequest);

    @RpcMethod
    void removeTaskInstanceLog(String taskInstanceLogAbsolutePath);

}
