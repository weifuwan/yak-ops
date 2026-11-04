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
