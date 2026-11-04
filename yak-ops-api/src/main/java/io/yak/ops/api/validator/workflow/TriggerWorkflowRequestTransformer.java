package io.yak.ops.api.validator.workflow;

import io.yak.ops.api.dto.workflow.WorkflowTriggerRequest;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.utils.WorkflowUtils;
import io.yak.ops.api.validator.ITransformer;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.plugin.task.api.utils.PropertyUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TriggerWorkflowRequestTransformer implements ITransformer<WorkflowTriggerRequest, TriggerWorkflowDTO> {

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Override
    public TriggerWorkflowDTO transform(WorkflowTriggerRequest workflowTriggerRequest) {
        TriggerWorkflowDTO triggerWorkflowDTO = TriggerWorkflowDTO.builder()
                .loginUser(workflowTriggerRequest.getLoginUser())
                .startNodes(WorkflowUtils.parseStartNodeList(workflowTriggerRequest.getStartNodes()))
                .failureStrategy(workflowTriggerRequest.getFailureStrategy())
                .taskDependType(workflowTriggerRequest.getTaskDependType())
                .execType(workflowTriggerRequest.getExecType())
                .warningType(workflowTriggerRequest.getWarningType())
                .warningGroupId(workflowTriggerRequest.getWarningGroupId())
                .workflowInstancePriority(workflowTriggerRequest.getWorkflowInstancePriority())
                .workerGroup(workflowTriggerRequest.getWorkerGroup())
                .tenantCode(workflowTriggerRequest.getTenantCode())
                .environmentCode(workflowTriggerRequest.getEnvironmentCode())
                .startParamList(
                        PropertyUtils.startParamsTransformPropertyList(workflowTriggerRequest.getStartParamList()))
                .dryRun(workflowTriggerRequest.getDryRun())
                .build();

        WorkflowDefinition workflowDefinition = workflowDefinitionDao
                .queryByCode(workflowTriggerRequest.getWorkflowDefinitionCode())
                .orElseThrow(() -> new ServiceException(
                        "Cannot find the workflow: " + workflowTriggerRequest.getWorkflowDefinitionCode()));

        triggerWorkflowDTO.setWorkflowDefinition(workflowDefinition);
        return triggerWorkflowDTO;
    }
}
