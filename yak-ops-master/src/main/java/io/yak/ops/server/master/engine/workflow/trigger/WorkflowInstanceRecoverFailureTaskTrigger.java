package io.yak.ops.server.master.engine.workflow.trigger;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksResponse;

import java.util.Date;

import org.springframework.stereotype.Component;

@Component
public class WorkflowInstanceRecoverFailureTaskTrigger
        extends
            AbstractWorkflowInstanceTrigger<WorkflowInstanceRecoverFailureTasksRequest, WorkflowInstanceRecoverFailureTasksResponse> {

    @Override
    protected WorkflowInstance constructWorkflowInstance(final WorkflowInstanceRecoverFailureTasksRequest workflowInstanceRecoverFailureTasksRequest) {
        WorkflowInstance workflowInstance =
                getWorkflowInstance(workflowInstanceRecoverFailureTasksRequest.getWorkflowInstanceId());
        workflowInstance.setStateWithDesc(WorkflowExecutionStatus.SUBMITTED_SUCCESS,
                CommandType.START_FAILURE_TASK_PROCESS.name());
        workflowInstance.setRunTimes(workflowInstance.getRunTimes() + 1);
        workflowInstance.setRestartTime(new Date());
        workflowInstance.setEndTime(null);
        return workflowInstance;
    }

    @Override
    protected Command constructTriggerCommand(final WorkflowInstanceRecoverFailureTasksRequest workflowInstanceRecoverFailureTasksRequest,
                                              final WorkflowInstance workflowInstance) {
        return Command.builder()
                .commandType(CommandType.START_FAILURE_TASK_PROCESS)
                .workflowDefinitionCode(workflowInstance.getWorkflowDefinitionCode())
                .workflowDefinitionVersion(workflowInstance.getWorkflowDefinitionVersion())
                .workflowInstanceId(workflowInstance.getId())
                .executorId(workflowInstanceRecoverFailureTasksRequest.getUserId())
                .startTime(new Date())
                .updateTime(new Date())
                .build();
    }

    @Override
    protected WorkflowInstanceRecoverFailureTasksResponse onTriggerSuccess(WorkflowInstance workflowInstance) {
        return WorkflowInstanceRecoverFailureTasksResponse.success();
    }
}
