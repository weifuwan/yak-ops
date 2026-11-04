package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.WorkFlowRelationDetail;
import io.yak.ops.dao.entity.WorkflowTaskLineage;

import java.util.List;

public interface WorkflowTaskLineageDao extends IDao<WorkflowTaskLineage> {

    int batchDeleteByWorkflowDefinitionCode(List<Long> workflowDefinitionCodes);

    int batchInsert(List<WorkflowTaskLineage> workflowTaskLineages);

    List<WorkflowTaskLineage> queryByProjectCode(long projectCode);

    List<WorkFlowRelationDetail> queryWorkFlowLineageByCode(long workflowDefinitionCode);

    List<WorkFlowRelationDetail> queryWorkFlowLineageByName(long projectCode,
                                                            String workflowDefinitionName);

    List<WorkflowTaskLineage> queryWorkFlowLineageByDept(long deptProjectCode,
                                                         long deptWorkflowDefinitionCode,
                                                         long deptTaskDefinitionCode);

    List<WorkflowTaskLineage> queryByWorkflowDefinitionCode(long workflowDefinitionCode);

    int updateWorkflowTaskLineage(List<WorkflowTaskLineage> workflowTaskLineages);
}
