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
import io.yak.ops.extract.master.transportor.workflow.WorkflowBackfillTriggerRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowBackfillTriggerResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstancePauseRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstancePauseResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRepeatRunningRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRepeatRunningResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceStopRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceStopResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowManualTriggerRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowManualTriggerResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowScheduleTriggerRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowScheduleTriggerResponse;

/**
 * Workflow instance controller used to do control operation for workflow instance.
 */
@RpcService
public interface IWorkflowControlClient {

    @RpcMethod
    WorkflowManualTriggerResponse manualTriggerWorkflow(final WorkflowManualTriggerRequest workflowManualTriggerRequest);

    @RpcMethod
    WorkflowBackfillTriggerResponse backfillTriggerWorkflow(final WorkflowBackfillTriggerRequest workflowBackfillTriggerRequest);

    @RpcMethod
    WorkflowScheduleTriggerResponse scheduleTriggerWorkflow(final WorkflowScheduleTriggerRequest workflowScheduleTriggerRequest);

    @RpcMethod
    WorkflowInstanceRepeatRunningResponse repeatTriggerWorkflowInstance(final WorkflowInstanceRepeatRunningRequest workflowInstanceRepeatRunningRequest);

    @RpcMethod
    WorkflowInstanceRecoverFailureTasksResponse triggerFromFailureTasks(final WorkflowInstanceRecoverFailureTasksRequest workflowInstanceRecoverFailureTasksRequest);

    @RpcMethod
    WorkflowInstanceRecoverSuspendTasksResponse triggerFromSuspendTasks(final WorkflowInstanceRecoverSuspendTasksRequest workflowInstanceRecoverSuspendTasksRequest);

    @RpcMethod
    WorkflowInstancePauseResponse pauseWorkflowInstance(final WorkflowInstancePauseRequest workflowInstancePauseRequest);

    @RpcMethod
    WorkflowInstanceStopResponse stopWorkflowInstance(final WorkflowInstanceStopRequest workflowInstanceStopRequest);
}
