package io.yak.ops.server.master.engine.workflow.trigger;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRepeatRunningRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRepeatRunningResponse;

import java.util.Date;

import org.springframework.stereotype.Component;

@Component
public class WorkflowInstanceRepeatTrigger
        extends
            AbstractWorkflowInstanceTrigger<WorkflowInstanceRepeatRunningRequest, WorkflowInstanceRepeatRunningResponse> {

    @Override
    protected WorkflowInstance constructWorkflowInstance(final WorkflowInstanceRepeatRunningRequest repeatRunningRequest) {
        return getWorkflowInstance(repeatRunningRequest.getWorkflowInstanceId());
    }

    @Override
    protected Command constructTriggerCommand(final WorkflowInstanceRepeatRunningRequest repeatRunningRequest,
                                              final WorkflowInstance workflowInstance) {
        return Command.builder()
                .commandType(CommandType.REPEAT_RUNNING)
                .workflowInstanceId(workflowInstance.getId())
                .workflowDefinitionCode(workflowInstance.getWorkflowDefinitionCode())
                .workflowDefinitionVersion(workflowInstance.getWorkflowDefinitionVersion())
                .executorId(repeatRunningRequest.getUserId())
                .workerGroup(workflowInstance.getWorkerGroup())
                .startTime(new Date())
                .updateTime(new Date())
                .build();
    }

    @Override
    protected WorkflowInstanceRepeatRunningResponse onTriggerSuccess(final WorkflowInstance workflowInstance) {
        return WorkflowInstanceRepeatRunningResponse.success();
    }
}
