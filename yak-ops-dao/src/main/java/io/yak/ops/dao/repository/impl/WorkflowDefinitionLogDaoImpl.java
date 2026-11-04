package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.WorkflowDefinitionLog;
import io.yak.ops.dao.mapper.WorkflowDefinitionLogMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.WorkflowDefinitionLogDao;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class WorkflowDefinitionLogDaoImpl extends BaseDao<WorkflowDefinitionLog, WorkflowDefinitionLogMapper>
        implements
            WorkflowDefinitionLogDao {

    public WorkflowDefinitionLogDaoImpl(@NonNull WorkflowDefinitionLogMapper workflowDefinitionLogMapper) {
        super(workflowDefinitionLogMapper);
    }

    @Override
    public WorkflowDefinitionLog queryByDefinitionCodeAndVersion(long workflowDefinitionCode,
                                                                 int workflowDefinitionVersion) {
        return mybatisMapper.queryByDefinitionCodeAndVersion(workflowDefinitionCode, workflowDefinitionVersion);
    }

    @Override
    public void deleteByWorkflowDefinitionCode(long workflowDefinitionCode) {
        mybatisMapper.deleteByWorkflowDefinitionCode(workflowDefinitionCode);
    }
}
