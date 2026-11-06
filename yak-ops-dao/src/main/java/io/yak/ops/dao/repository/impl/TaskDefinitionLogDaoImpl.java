package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.mapper.TaskDefinitionLogMapper;
import io.yak.ops.dao.mapper.WorkflowTaskRelationLogMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.TaskDefinitionLogDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.NonNull;

/**
 * Task Definition Log DAP implementation
 */
@Repository
public class TaskDefinitionLogDaoImpl extends BaseDao<TaskDefinitionLog, TaskDefinitionLogMapper>
        implements
            TaskDefinitionLogDao {

    @Autowired
    private WorkflowTaskRelationLogMapper workflowTaskRelationLogMapper;

    public TaskDefinitionLogDaoImpl(@NonNull TaskDefinitionLogMapper taskDefinitionLogMapper) {
        super(taskDefinitionLogMapper);
    }

    @Override
    public List<TaskDefinitionLog> queryByWorkflowDefinitionCodeAndVersion(Long workflowDefinitionCode,
                                                                           Integer workflowDefinitionVersion) {

        List<WorkflowTaskRelation> workflowTaskRelationLogs = workflowTaskRelationLogMapper
                .queryByWorkflowCodeAndVersion(workflowDefinitionCode, workflowDefinitionVersion)
                .stream()
                .map(p -> (WorkflowTaskRelation) p)
                .collect(Collectors.toList());
        return queryTaskDefineLogList(workflowTaskRelationLogs);
    }

    @Override
    public List<TaskDefinitionLog> queryTaskDefineLogList(List<WorkflowTaskRelation> workflowTaskRelations) {
        if (CollectionUtils.isEmpty(workflowTaskRelations)) {
            return Collections.emptyList();
        }
        Set<TaskDefinition> taskDefinitionSet = workflowTaskRelations.stream()
                .filter(p -> p.getPostTaskCode() > 0)
                .map(p -> new TaskDefinition(p.getPostTaskCode(), p.getPostTaskVersion()))
                .collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(taskDefinitionSet)) {
            return Collections.emptyList();
        }
        return mybatisMapper.queryByTaskDefinitions(taskDefinitionSet);
    }

    @Override
    public List<TaskDefinitionLog> queryVersions(long taskCode) {
        return mybatisMapper.selectList(new QueryWrapper<TaskDefinitionLog>().lambda()
                .eq(TaskDefinitionLog::getCode, taskCode)
                .orderByDesc(TaskDefinitionLog::getVersion));
    }

    @Override
    public IPage<TaskDefinitionLog> queryVersionsPaging(Page<TaskDefinitionLog> page,
                                                        long taskCode, long projectCode) {
        return mybatisMapper.queryTaskDefinitionVersionsPaging(page, taskCode, projectCode);
    }

    @Override
    public void deleteByTaskDefinitionCodes(Set<Long> taskDefinitionCodes) {
        if (CollectionUtils.isEmpty(taskDefinitionCodes)) {
            return;
        }
        mybatisMapper.deleteByTaskDefinitionCodes(taskDefinitionCodes);
    }
}
