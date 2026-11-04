package io.yak.ops.server.master.engine.workflow.listener;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.master.command.BackfillWorkflowCommandParam;
import io.yak.ops.extract.master.command.ICommandParam;
import io.yak.ops.extract.master.transportor.workflow.WorkflowBackfillTriggerRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowBackfillTriggerResponse;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.WorkflowLifecycleEventType;
import io.yak.ops.server.master.engine.workflow.trigger.WorkflowBackfillTrigger;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowSuccessLifecycleListener implements IWorkflowLifecycleListener {

    @Autowired
    private WorkflowBackfillTrigger workflowBackfillTrigger;

    @Override
    public void notifyWorkflowLifecycleEvent(final IWorkflowExecution workflowExecution,
                                             final AbstractWorkflowLifecycleLifecycleEvent lifecycleEvent) {
        final WorkflowInstance workflowInstance = workflowExecution.getWorkflowInstance();
        if (Flag.YES == workflowInstance.getIsSubWorkflow()) {
            // The sub workflow does not need to generate the backfill command
            // Since the parent workflow will trigger the task to generate the sub workflow instance.
            return;
        }

        final ICommandParam commandParam =
                JSONUtils.parseObject(workflowInstance.getCommandParam(), ICommandParam.class);
        if (commandParam == null) {
            log.warn("Command param: {} is invalid for workflow: {}", workflowInstance.getCommandParam(),
                    workflowInstance.getName());
            return;
        }
        if (commandParam.getCommandType() != CommandType.COMPLEMENT_DATA) {
            return;
        }
        generateNextBackfillCommand((BackfillWorkflowCommandParam) commandParam, workflowInstance);
    }

    private void generateNextBackfillCommand(final BackfillWorkflowCommandParam commandParam,
                                             final WorkflowInstance workflowInstance) {
        // Generate next backfill command
        final List<String> backfillTimeList = commandParam.getBackfillTimeList();
        backfillTimeList.remove(DateUtils.dateToString(workflowInstance.getScheduleTime()));
        if (CollectionUtils.isEmpty(backfillTimeList)) {
            return;
        }
        final WorkflowBackfillTriggerRequest backfillTriggerRequest = WorkflowBackfillTriggerRequest.builder()
                .userId(workflowInstance.getExecutorId())
                .backfillTimeList(backfillTimeList)
                .workflowCode(workflowInstance.getWorkflowDefinitionCode())
                .workflowVersion(workflowInstance.getWorkflowDefinitionVersion())
                .startNodes(commandParam.getStartNodes())
                .failureStrategy(workflowInstance.getFailureStrategy())
                .taskDependType(workflowInstance.getTaskDependType())
                .warningType(workflowInstance.getWarningType())
                .warningGroupId(workflowInstance.getWarningGroupId())
                .workflowInstancePriority(workflowInstance.getWorkflowInstancePriority())
                .workerGroup(workflowInstance.getWorkerGroup())
                .tenantCode(workflowInstance.getTenantCode())
                .environmentCode(workflowInstance.getEnvironmentCode())
                .startParamList(commandParam.getCommandParams())
                .dryRun(Flag.of(workflowInstance.getDryRun()))
                .build();
        final WorkflowBackfillTriggerResponse backfillTriggerResponse =
                workflowBackfillTrigger.triggerWorkflow(backfillTriggerRequest);
        if (!backfillTriggerResponse.isSuccess()) {
            log.warn("Backfill workflow failed: {}", backfillTriggerResponse.getMessage());
        }
    }

    @Override
    public boolean match(AbstractWorkflowLifecycleLifecycleEvent event) {
        return event.getEventType() == WorkflowLifecycleEventType.SUCCEED;
    }

}
