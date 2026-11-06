package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.mapper.WorkflowTaskRelationMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class WorkflowTaskRelationDaoImpl extends BaseDao<WorkflowTaskRelation, WorkflowTaskRelationMapper>
        implements
            WorkflowTaskRelationDao {

    public WorkflowTaskRelationDaoImpl(@NonNull WorkflowTaskRelationMapper workflowTaskRelationMapper) {
        super(workflowTaskRelationMapper);
    }

    @Override
    public List<WorkflowTaskRelation> queryByWorkflowDefinitionCode(long workflowDefinitionCode) {
        return mybatisMapper.queryByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public List<WorkflowTaskRelation> queryByTaskCode(long taskCode) {
        return mybatisMapper.queryByTaskCode(taskCode);
    }

    @Override
    public int batchInsert(List<WorkflowTaskRelation> taskRelationList) {
        return mybatisMapper.batchInsert(taskRelationList);
    }

    @Override
    public List<WorkflowTaskRelation> queryUpstreamByCode(long projectCode, long taskCode) {
        return mybatisMapper.queryUpstreamByCode(projectCode, taskCode);
    }

    @Override
    public List<WorkflowTaskRelation> queryUpstreamByCodes(long projectCode, long taskCode, Long[] preTaskCodes) {
        return mybatisMapper.queryUpstreamByCodes(projectCode, taskCode, preTaskCodes);
    }

    @Override
    public List<WorkflowTaskRelation> queryWorkflowTaskRelationsByWorkflowDefinitionCode(long workflowDefinitionCode,
                                                                                         Integer workflowDefinitionVersion) {
        return mybatisMapper.queryWorkflowTaskRelationsByWorkflowDefinitionCode(workflowDefinitionCode,
                workflowDefinitionVersion);
    }

    @Override
    public List<WorkflowTaskRelation> queryDownstreamByWorkflowDefinitionCode(long workflowDefinitionCode) {
        return mybatisMapper.queryDownstreamByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public void deleteByWorkflowDefinitionCodeAndVersion(long workflowDefinitionCode, int workflowDefinitionVersion) {
        mybatisMapper.deleteByWorkflowDefinitionCodeAndVersion(workflowDefinitionCode, workflowDefinitionVersion);
    }

    @Override
    public List<WorkflowTaskRelation> queryByCode(long projectCode, long workflowDefinitionCode, long preTaskCode,
                                                  long postTaskCode) {
        return mybatisMapper.queryByCode(projectCode, workflowDefinitionCode, preTaskCode, postTaskCode);
    }
}
