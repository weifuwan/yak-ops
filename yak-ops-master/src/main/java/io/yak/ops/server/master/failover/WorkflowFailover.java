package io.yak.ops.server.master.failover;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.model.WorkflowInstanceSummaryDto;
import io.yak.ops.dao.repository.CommandDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.extract.master.command.WorkflowFailoverCommandParam;
import io.yak.ops.server.master.metrics.WorkflowInstanceMetrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowFailover {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private CommandDao commandDao;

    @Transactional
    public void failoverWorkflow(final WorkflowInstanceSummaryDto workflowInstance) {
        workflowInstanceDao.updateWorkflowInstanceState(
                workflowInstance.getId(),
                workflowInstance.getState(),
                WorkflowExecutionStatus.FAILOVER);
        WorkflowInstanceMetrics.recordWorkflowInstanceFailover(workflowInstance.getWorkflowDefinitionCode());

        final WorkflowFailoverCommandParam failoverWorkflowCommandParam = WorkflowFailoverCommandParam.builder()
                .workflowExecutionStatus(workflowInstance.getState())
                .build();

        final Command failoverCommand = Command.builder()
                .commandParam(JSONUtils.toJsonString(failoverWorkflowCommandParam))
                .commandType(CommandType.RECOVER_TOLERANCE_FAULT_PROCESS)
                .workflowDefinitionCode(workflowInstance.getWorkflowDefinitionCode())
                .workflowDefinitionVersion(workflowInstance.getWorkflowDefinitionVersion())
                .workflowInstanceId(workflowInstance.getId())
                .build();
        commandDao.insert(failoverCommand);
        log.info("Success failover workflowInstance: [id={}, name={}, state={}]",
                workflowInstance.getId(),
                workflowInstance.getName(),
                workflowInstance.getState().name());
    }

}
