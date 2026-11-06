package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.TaskDefinition;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Task Instance DAO
 */
public interface TaskDefinitionDao extends IDao<TaskDefinition> {

    /**
     * Get list of task definition by process definition code
     *
     * @param workflowDefinitionCode process definition code
     * @return list of task definition
     */
    List<TaskDefinition> getTaskDefinitionListByDefinition(long workflowDefinitionCode);

    /**
     * Query task definition by code and version
     * @param taskCode task code
     * @param taskDefinitionVersion task definition version
     * @return task definition
     */
    TaskDefinition findTaskDefinition(long taskCode, int taskDefinitionVersion);

    void deleteByWorkflowDefinitionCodeAndVersion(long workflowDefinitionCode, int workflowDefinitionVersion);

    void deleteByTaskDefinitionCodes(Set<Long> needToDeleteTaskDefinitionCodes);

    List<TaskDefinition> queryByCodes(Collection<Long> taskDefinitionCodes);

    /**
     * Query task definition by code
     * @param taskCode task code
     * @return task definition
     */
    TaskDefinition queryByCode(long taskCode);

    IPage<TaskDefinition> queryDataSyncPage(IPage<TaskDefinition> page, long projectCode,
                                             String taskType, String keyword, Long taskCode,
                                             Integer releaseState);

    TaskDefinition queryByName(long projectCode, long workflowDefinitionCode, String taskName);

    List<TaskDefinition> queryByWorkerGroup(String workerGroup);

    long countByEnvironmentCode(long environmentCode);

    List<TaskDefinition> queryByEnvironmentCodeAndWorkerGroup(long environmentCode, String workerGroup);

    List<String> queryAllTaskDefinitionWorkerGroups(long projectCode);
}
