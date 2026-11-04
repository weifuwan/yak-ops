package io.yak.ops.api.permission;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.plugin.task.api.parameters.SubWorkflowParameters;
import io.yak.ops.plugin.task.api.utils.TaskTypeUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskSubWorkflowPermissionChecker {

    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final ProjectService projectService;

    public TaskSubWorkflowPermissionChecker(WorkflowDefinitionDao workflowDefinitionDao,
                                            ProjectService projectService) {
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.projectService = projectService;
    }

    public void checkPermission(User loginUser, Collection<? extends TaskDefinition> taskDefinitions) {
        if (taskDefinitions == null || taskDefinitions.isEmpty()) {
            return;
        }

        Set<Long> subWorkflowDefinitionCodes = new HashSet<>();
        for (TaskDefinition taskDefinition : taskDefinitions) {
            if (!TaskTypeUtils.isSubWorkflowTask(taskDefinition.getTaskType())) {
                continue;
            }
            SubWorkflowParameters subWorkflowParameters =
                    JSONUtils.parseObject(taskDefinition.getTaskParams(), SubWorkflowParameters.class);
            if (subWorkflowParameters.getWorkflowDefinitionCode() > 0) {
                subWorkflowDefinitionCodes.add(subWorkflowParameters.getWorkflowDefinitionCode());
            }
        }

        if (subWorkflowDefinitionCodes.isEmpty()) {
            return;
        }

        List<WorkflowDefinition> subWorkflowDefinitions =
                workflowDefinitionDao.queryByCodes(subWorkflowDefinitionCodes);
        if (subWorkflowDefinitions == null) {
            log.warn("Referenced sub workflow is unavailable, userId:{}.", loginUser.getId());
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
        Set<Long> existingSubWorkflowDefinitionCodes =
                subWorkflowDefinitions.stream().map(WorkflowDefinition::getCode).collect(Collectors.toSet());
        if (!existingSubWorkflowDefinitionCodes.containsAll(subWorkflowDefinitionCodes)) {
            log.warn("Referenced sub workflow is unavailable, userId:{}.", loginUser.getId());
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }

        Set<Long> subWorkflowProjectCodes = subWorkflowDefinitions.stream()
                .map(WorkflowDefinition::getProjectCode)
                .collect(Collectors.toSet());
        try {
            for (Long projectCode : subWorkflowProjectCodes) {
                projectService.checkHasProjectWritePermissionThrowException(loginUser, projectCode);
            }
        } catch (ServiceException ex) {
            log.warn("Referenced sub workflow is unavailable, userId:{}.", loginUser.getId());
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
    }
}
