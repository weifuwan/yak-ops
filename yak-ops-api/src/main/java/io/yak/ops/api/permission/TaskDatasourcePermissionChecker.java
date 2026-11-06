package io.yak.ops.api.permission;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.resource.AbstractResourceParameters;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskDatasourcePermissionChecker {

    private final ResourcePermissionCheckService resourcePermissionCheckService;
    private final DataSourceDao dataSourceDao;

    public TaskDatasourcePermissionChecker(ResourcePermissionCheckService resourcePermissionCheckService,
                                           DataSourceDao dataSourceDao) {
        this.resourcePermissionCheckService = resourcePermissionCheckService;
        this.dataSourceDao = dataSourceDao;
    }

    public void checkPermission(User loginUser, Collection<? extends TaskDefinition> taskDefinitions) {
        if (taskDefinitions == null || taskDefinitions.isEmpty()) {
            return;
        }

        Set<Integer> datasourceIds = new HashSet<>();
        for (TaskDefinition taskDefinition : taskDefinitions) {
            AbstractParameters taskParameters = TaskPluginManager.parseTaskParameters(
                    taskDefinition.getTaskType(), taskDefinition.getTaskParams());
            ResourceParametersHelper resources = taskParameters.getResources();
            if (resources == null) {
                continue;
            }
            Map<Integer, AbstractResourceParameters> datasourceResources =
                    resources.getResourceMap(ResourceType.DATASOURCE);
            if (datasourceResources == null) {
                continue;
            }
            for (Integer datasourceId : datasourceResources.keySet()) {
                if (datasourceId == null || datasourceId <= 0) {
                    continue;
                }
                if (taskDefinition.getProjectCode() > 0) {
                    if (dataSourceDao.queryByIdAndProjectCode(datasourceId, taskDefinition.getProjectCode()) == null) {
                        throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
                    }
                } else {
                    datasourceIds.add(datasourceId);
                }
            }
        }

        if (datasourceIds.isEmpty()) {
            return;
        }

        int userId = loginUser.getUserType() == UserType.ADMIN_USER ? 0 : loginUser.getId();
        Integer[] datasourceIdArray = datasourceIds.toArray(new Integer[0]);
        if (!resourcePermissionCheckService.resourcePermissionCheck(
                AuthorizationType.DATASOURCE, datasourceIdArray, userId, log)) {
            log.warn("User does not have permission to use datasource referenced by task, userId:{}.",
                    loginUser.getId());
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
    }
}
