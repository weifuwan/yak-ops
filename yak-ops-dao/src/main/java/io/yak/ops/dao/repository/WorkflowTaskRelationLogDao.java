package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.WorkflowTaskRelationLog;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface WorkflowTaskRelationLogDao extends IDao<WorkflowTaskRelationLog> {

    List<WorkflowTaskRelationLog> queryByWorkflowDefinitionCode(long workflowDefinitionCode);

    void deleteByWorkflowDefinitionCode(long workflowDefinitionCode);

    /**
     * batch insert workflow task relation
     *
     * @param taskRelationList taskRelationList
     * @return int
     */
    int batchInsert(@Param("taskRelationList") List<WorkflowTaskRelationLog> taskRelationList);
}
