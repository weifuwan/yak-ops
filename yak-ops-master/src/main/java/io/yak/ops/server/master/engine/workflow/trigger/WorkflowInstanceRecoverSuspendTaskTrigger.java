package io.yak.ops.server.master.engine.workflow.trigger;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksResponse;

import java.util.Date;

import org.springframework.stereotype.Component;

@Component
public class WorkflowInstanceRecoverSuspendTaskTrigger
        extends
            AbstractWorkflowInstanceTrigger<WorkflowInstanceRecoverSuspendTasksRequest, WorkflowInstanceRecoverSuspendTasksResponse> {

    @Override
    protected WorkflowInstance constructWorkflowInstance(final WorkflowInstanceRecoverSuspendTasksRequest workflowInstanceRecoverSuspendTasksRequest) {
        final WorkflowInstance workflowInstance =
                getWorkflowInstance(workflowInstanceRecoverSuspendTasksRequest.getWorkflowInstanceId());
        workflowInstance.setStateWithDesc(WorkflowExecutionStatus.SUBMITTED_SUCCESS,
                CommandType.START_FAILURE_TASK_PROCESS.name());
        workflowInstance.setRunTimes(workflowInstance.getRunTimes() + 1);
        workflowInstance.setRestartTime(new Date());
        workflowInstance.setEndTime(null);
        return workflowInstance;
    }

    @Override
    protected Command constructTriggerCommand(final WorkflowInstanceRecoverSuspendTasksRequest workflowInstanceRecoverSuspendTasksRequest,
                                              final WorkflowInstance workflowInstance) {
        return Command.builder()
                .commandType(CommandType.RECOVER_SUSPENDED_PROCESS)
                .workflowDefinitionCode(workflowInstance.getWorkflowDefinitionCode())
                .workflowDefinitionVersion(workflowInstance.getWorkflowDefinitionVersion())
                .workflowInstanceId(workflowInstance.getId())
                .executorId(workflowInstanceRecoverSuspendTasksRequest.getUserId())
                .startTime(new Date())
                .updateTime(new Date())
                .build();
    }

    @Override
    protected WorkflowInstanceRecoverSuspendTasksResponse onTriggerSuccess(WorkflowInstance workflowInstance) {
        return WorkflowInstanceRecoverSuspendTasksResponse.success();
    }
}
