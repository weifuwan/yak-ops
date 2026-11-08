package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.dao.mapper.TaskDefinitionLogMapper;
import io.yak.ops.dao.mapper.TaskDefinitionMapper;
import io.yak.ops.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.dao.mapper.WorkflowTaskRelationLogMapper;
import io.yak.ops.dao.model.DataSyncOverviewTaskRow;
import io.yak.ops.dao.model.DataSyncTaskPageRow;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.google.common.collect.Lists;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class TaskDefinitionDaoImpl extends BaseDao<TaskDefinition, TaskDefinitionMapper> implements TaskDefinitionDao {

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Autowired
    private WorkflowTaskRelationLogMapper workflowTaskRelationLogMapper;

    @Autowired
    private TaskDefinitionLogMapper taskDefinitionLogMapper;

    public TaskDefinitionDaoImpl(@NonNull TaskDefinitionMapper taskDefinitionMapper) {
        super(taskDefinitionMapper);
    }

    @Override
    public List<TaskDefinition> getTaskDefinitionListByDefinition(long workflowDefinitionCode) {
        WorkflowDefinition workflowDefinition = workflowDefinitionMapper.queryByCode(workflowDefinitionCode);
        if (workflowDefinition == null) {
            log.error("Cannot find process definition, code: {}", workflowDefinitionCode);
            return Lists.newArrayList();
        }

        List<WorkflowTaskRelationLog> processTaskRelations =
                workflowTaskRelationLogMapper.queryByWorkflowCodeAndVersion(
                        workflowDefinition.getCode(), workflowDefinition.getVersion());
        Set<TaskDefinition> taskDefinitionSet = processTaskRelations
                .stream()
                .filter(p -> p.getPostTaskCode() > 0)
                .map(p -> new TaskDefinition(p.getPostTaskCode(), p.getPostTaskVersion()))
                .collect(Collectors.toSet());

        if (taskDefinitionSet.isEmpty()) {
            return Lists.newArrayList();
        }
        List<TaskDefinitionLog> taskDefinitionLogs = taskDefinitionLogMapper.queryByTaskDefinitions(taskDefinitionSet);
        return Lists.newArrayList(taskDefinitionLogs);
    }

    @Override
    public TaskDefinition findTaskDefinition(long taskCode, int taskDefinitionVersion) {
        return taskDefinitionLogMapper.queryByDefinitionCodeAndVersion(taskCode, taskDefinitionVersion);
    }

    @Override
    public boolean compareAndSetDataSyncRevision(long projectCode, long taskCode, int expectedRevision) {
        return mybatisMapper.updateDataSyncRevision(projectCode, taskCode, expectedRevision, expectedRevision + 1) == 1;
    }

    @Override
    public void deleteByWorkflowDefinitionCodeAndVersion(long workflowDefinitionCode, int workflowDefinitionVersion) {
        mybatisMapper.deleteByWorkflowDefinitionCodeAndVersion(workflowDefinitionCode, workflowDefinitionVersion);
    }

    @Override
    public void deleteByTaskDefinitionCodes(Set<Long> needToDeleteTaskDefinitionCodes) {
        if (CollectionUtils.isEmpty(needToDeleteTaskDefinitionCodes)) {
            return;
        }
        mybatisMapper.deleteByBatchCodes(new ArrayList<>(needToDeleteTaskDefinitionCodes));
    }

    @Override
    public List<TaskDefinition> queryByCodes(Collection<Long> taskDefinitionCodes) {
        if (CollectionUtils.isEmpty(taskDefinitionCodes)) {
            return Collections.emptyList();
        }
        return mybatisMapper.queryByCodeList(taskDefinitionCodes);
    }

    @Override
    public TaskDefinition queryByCode(long taskCode) {
        return mybatisMapper.queryByCode(taskCode);
    }

    @Override
    public IPage<DataSyncTaskPageRow> queryDataSyncPage(IPage<DataSyncTaskPageRow> page, long projectCode,
                                                    String taskType, String keyword, Long taskCode,
                                                    Integer releaseState, List<Long> taskCodes) {
        return mybatisMapper.queryDataSyncPage(page, projectCode, taskType, keyword, taskCode, releaseState, taskCodes);
    }

    @Override
    public List<DataSyncOverviewTaskRow> queryDataSyncOverview(long projectCode) {
        return mybatisMapper.queryDataSyncOverview(projectCode);
    }

    @Override
    public TaskDefinition queryByName(long projectCode, long workflowDefinitionCode, String taskName) {
        return mybatisMapper.queryByName(projectCode, workflowDefinitionCode, taskName);
    }

    @Override
    public List<TaskDefinition> queryByWorkerGroup(String workerGroup) {
        return mybatisMapper.selectList(
                new QueryWrapper<TaskDefinition>().lambda().eq(TaskDefinition::getWorkerGroup, workerGroup));
    }

    @Override
    public long countByEnvironmentCode(long environmentCode) {
        return mybatisMapper.selectCount(
                new QueryWrapper<TaskDefinition>().lambda().eq(TaskDefinition::getEnvironmentCode, environmentCode));
    }

    @Override
    public List<TaskDefinition> queryByEnvironmentCodeAndWorkerGroup(long environmentCode, String workerGroup) {
        return mybatisMapper.selectList(
                new QueryWrapper<TaskDefinition>().lambda()
                        .eq(TaskDefinition::getEnvironmentCode, environmentCode)
                        .eq(TaskDefinition::getWorkerGroup, workerGroup));
    }

    @Override
    public List<String> queryAllTaskDefinitionWorkerGroups(long projectCode) {
        return mybatisMapper.queryAllTaskDefinitionWorkerGroups(projectCode);
    }
}
