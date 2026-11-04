package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.dao.mapper.WorkflowTaskRelationLogMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationLogDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class WorkflowTaskRelationLogDaoImpl extends BaseDao<WorkflowTaskRelationLog, WorkflowTaskRelationLogMapper>
        implements
            WorkflowTaskRelationLogDao {

    public WorkflowTaskRelationLogDaoImpl(@NonNull WorkflowTaskRelationLogMapper workflowTaskRelationLogMapper) {
        super(workflowTaskRelationLogMapper);
    }

    @Override
    public List<WorkflowTaskRelationLog> queryByWorkflowDefinitionCode(long workflowDefinitionCode) {
        return mybatisMapper.queryByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public void deleteByWorkflowDefinitionCode(long workflowDefinitionCode) {
        mybatisMapper.deleteByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public int batchInsert(List<WorkflowTaskRelationLog> taskRelationList) {
        if (CollectionUtils.isEmpty(taskRelationList)) {
            return 0;
        }
        return mybatisMapper.batchInsert(taskRelationList);
    }
}
