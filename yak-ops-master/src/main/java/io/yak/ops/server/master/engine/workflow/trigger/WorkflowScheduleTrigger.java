package io.yak.ops.server.master.engine.workflow.trigger;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.utils.EnvironmentUtils;
import io.yak.ops.dao.utils.WorkerGroupUtils;
import io.yak.ops.extract.master.command.ScheduleWorkflowCommandParam;
import io.yak.ops.extract.master.transportor.workflow.WorkflowScheduleTriggerRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowScheduleTriggerResponse;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Date;

import org.springframework.stereotype.Component;

@Component
public class WorkflowScheduleTrigger
        extends
            AbstractWorkflowTrigger<WorkflowScheduleTriggerRequest, WorkflowScheduleTriggerResponse> {

    @Override
    protected ImmutablePair<WorkflowDefinition, WorkflowInstance> constructWorkflowInstance(WorkflowScheduleTriggerRequest scheduleTriggerRequest) {
        final CommandType commandType = CommandType.SCHEDULER;
        final Long workflowCode = scheduleTriggerRequest.getWorkflowCode();
        final Integer workflowVersion = scheduleTriggerRequest.getWorkflowVersion();
        final WorkflowDefinition workflowDefinition = getProcessDefinition(workflowCode, workflowVersion);

        final WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setWorkflowDefinitionCode(workflowDefinition.getCode());
        workflowInstance.setWorkflowDefinitionVersion(workflowDefinition.getVersion());
        workflowInstance.setProjectCode(workflowDefinition.getProjectCode());
        workflowInstance.setCommandType(commandType);
        workflowInstance.setStateWithDesc(WorkflowExecutionStatus.SUBMITTED_SUCCESS, commandType.name());
        workflowInstance.setRecovery(Flag.NO);
        workflowInstance.setScheduleTime(scheduleTriggerRequest.getScheduleTIme());
        workflowInstance.setStartTime(new Date());
        workflowInstance.setRestartTime(workflowInstance.getStartTime());
        workflowInstance.setRunTimes(1);
        workflowInstance.setName(String.join("-", workflowDefinition.getName(), DateUtils.getCurrentTimeStamp()));
        workflowInstance.setTaskDependType(scheduleTriggerRequest.getTaskDependType());
        workflowInstance.setFailureStrategy(scheduleTriggerRequest.getFailureStrategy());
        workflowInstance
                .setWarningType(ObjectUtils.defaultIfNull(scheduleTriggerRequest.getWarningType(), WarningType.NONE));
        workflowInstance.setWarningGroupId(scheduleTriggerRequest.getWarningGroupId());
        workflowInstance.setExecutorId(scheduleTriggerRequest.getUserId());
        workflowInstance.setExecutorName(getExecutorUser(scheduleTriggerRequest.getUserId()).getUserName());
        workflowInstance.setTenantCode(scheduleTriggerRequest.getTenantCode());
        workflowInstance.setIsSubWorkflow(Flag.NO);
        workflowInstance.addHistoryCmd(commandType);
        workflowInstance.setWorkflowInstancePriority(scheduleTriggerRequest.getWorkflowInstancePriority());
        workflowInstance
                .setWorkerGroup(WorkerGroupUtils.getWorkerGroupOrDefault(scheduleTriggerRequest.getWorkerGroup()));
        workflowInstance.setEnvironmentCode(
                EnvironmentUtils.getEnvironmentCodeOrDefault(scheduleTriggerRequest.getEnvironmentCode()));
        workflowInstance.setTimeout(workflowDefinition.getTimeout());
        workflowInstance.setDryRun(scheduleTriggerRequest.getDryRun().getCode());
        return ImmutablePair.of(workflowDefinition, workflowInstance);
    }

    @Override
    protected Command constructTriggerCommand(final WorkflowScheduleTriggerRequest scheduleTriggerRequest,
                                              final WorkflowInstance workflowInstance) {
        final ScheduleWorkflowCommandParam scheduleWorkflowCommandParam = ScheduleWorkflowCommandParam.builder()
                .timeZone(scheduleTriggerRequest.getTimezoneId())
                .build();
        return Command.builder()
                .commandType(CommandType.SCHEDULER)
                .workflowDefinitionCode(scheduleTriggerRequest.getWorkflowCode())
                .workflowDefinitionVersion(scheduleTriggerRequest.getWorkflowVersion())
                .workflowInstanceId(workflowInstance.getId())
                .workflowInstancePriority(workflowInstance.getWorkflowInstancePriority())
                .commandParam(JSONUtils.toJsonString(scheduleWorkflowCommandParam))
                .build();
    }

    @Override
    protected WorkflowScheduleTriggerResponse onTriggerSuccess(WorkflowInstance workflowInstance) {
        return WorkflowScheduleTriggerResponse.success(workflowInstance.getId());
    }
}
