package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.WorkFlowRelationDetail;
import io.yak.ops.dao.entity.WorkflowTaskLineage;
import io.yak.ops.dao.mapper.WorkflowTaskLineageMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.WorkflowTaskLineageDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class WorkflowTaskLineageDaoImpl extends BaseDao<WorkflowTaskLineage, WorkflowTaskLineageMapper>
        implements
            WorkflowTaskLineageDao {

    public WorkflowTaskLineageDaoImpl(@NonNull WorkflowTaskLineageMapper workflowTaskLineageMapper) {
        super(workflowTaskLineageMapper);
    }

    @Override
    public int batchDeleteByWorkflowDefinitionCode(List<Long> workflowDefinitionCodes) {
        if (CollectionUtils.isEmpty(workflowDefinitionCodes)) {
            return 0;
        }
        return mybatisMapper.batchDeleteByWorkflowDefinitionCode(workflowDefinitionCodes);
    }

    @Override
    public int batchInsert(List<WorkflowTaskLineage> workflowTaskLineages) {
        if (CollectionUtils.isEmpty(workflowTaskLineages)) {
            return 0;
        }
        return mybatisMapper.batchInsert(workflowTaskLineages);
    }

    @Override
    public List<WorkflowTaskLineage> queryByProjectCode(long projectCode) {
        return mybatisMapper.queryByProjectCode(projectCode);
    }

    @Override
    public List<WorkFlowRelationDetail> queryWorkFlowLineageByCode(long workflowDefinitionCode) {
        return mybatisMapper.queryWorkFlowLineageByCode(workflowDefinitionCode);
    }

    @Override
    public List<WorkFlowRelationDetail> queryWorkFlowLineageByName(long projectCode, String workflowDefinitionName) {
        return mybatisMapper.queryWorkFlowLineageByName(projectCode, workflowDefinitionName);
    }

    @Override
    public List<WorkflowTaskLineage> queryWorkFlowLineageByDept(long deptProjectCode, long deptWorkflowDefinitionCode,
                                                                long deptTaskDefinitionCode) {
        return mybatisMapper.queryWorkFlowLineageByDept(deptProjectCode, deptWorkflowDefinitionCode,
                deptTaskDefinitionCode);
    }

    @Override
    public List<WorkflowTaskLineage> queryByWorkflowDefinitionCode(long workflowDefinitionCode) {
        return mybatisMapper.queryByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public int updateWorkflowTaskLineage(List<WorkflowTaskLineage> workflowTaskLineages) {
        if (CollectionUtils.isEmpty(workflowTaskLineages)) {
            return 0;
        }
        this.batchDeleteByWorkflowDefinitionCode(
                workflowTaskLineages.stream().map(WorkflowTaskLineage::getWorkflowDefinitionCode)
                        .distinct().collect(Collectors.toList()));
        return mybatisMapper.batchInsert(workflowTaskLineages);
    }
}
