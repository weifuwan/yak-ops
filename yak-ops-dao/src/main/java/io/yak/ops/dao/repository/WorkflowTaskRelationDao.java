package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.WorkflowTaskRelation;

import java.util.List;

public interface WorkflowTaskRelationDao extends IDao<WorkflowTaskRelation> {

    List<WorkflowTaskRelation> queryByWorkflowDefinitionCode(long workflowDefinitionCode);

    List<WorkflowTaskRelation> queryByTaskCode(long taskCode);

    List<WorkflowTaskRelation> queryByTaskCodes(Long[] taskCodes);

    int batchInsert(List<WorkflowTaskRelation> taskRelationList);

    List<WorkflowTaskRelation> queryUpstreamByCode(long projectCode, long taskCode);

    List<WorkflowTaskRelation> queryUpstreamByCodes(long projectCode, long taskCode, Long[] preTaskCodes);

    List<WorkflowTaskRelation> queryWorkflowTaskRelationsByWorkflowDefinitionCode(long workflowDefinitionCode,
                                                                                  Integer workflowDefinitionVersion);

    List<WorkflowTaskRelation> queryDownstreamByWorkflowDefinitionCode(long workflowDefinitionCode);

    void deleteByWorkflowDefinitionCodeAndVersion(long workflowDefinitionCode, int workflowDefinitionVersion);

    List<WorkflowTaskRelation> queryByCode(long projectCode, long workflowDefinitionCode, long preTaskCode,
                                           long postTaskCode);
}
