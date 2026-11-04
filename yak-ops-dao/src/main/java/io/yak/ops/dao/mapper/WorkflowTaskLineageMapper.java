package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.WorkFlowRelationDetail;
import io.yak.ops.dao.entity.WorkflowTaskLineage;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface WorkflowTaskLineageMapper extends BaseMapper<WorkflowTaskLineage> {

    int batchDeleteByWorkflowDefinitionCode(@Param("workflowDefinitionCodes") List<Long> workflowDefinitionCodes);

    int batchInsert(@Param("workflowTaskLineages") List<WorkflowTaskLineage> workflowTaskLineages);

    List<WorkflowTaskLineage> queryByProjectCode(@Param("projectCode") long projectCode);

    List<WorkFlowRelationDetail> queryWorkFlowLineageByCode(@Param("workflowDefinitionCode") long workflowDefinitionCode);

    List<WorkFlowRelationDetail> queryWorkFlowLineageByName(@Param("projectCode") long projectCode,
                                                            @Param("workflowDefinitionName") String workflowDefinitionName);

    List<WorkflowTaskLineage> queryWorkFlowLineageByDept(@Param("deptProjectCode") long deptProjectCode,
                                                         @Param("deptWorkflowDefinitionCode") long deptWorkflowDefinitionCode,
                                                         @Param("deptTaskDefinitionCode") long deptTaskDefinitionCode);

    List<WorkflowTaskLineage> queryByWorkflowDefinitionCode(@Param("workflowDefinitionCode") long workflowDefinitionCode);

}
