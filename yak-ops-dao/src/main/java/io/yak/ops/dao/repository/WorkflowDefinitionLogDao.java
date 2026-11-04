package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.WorkflowDefinitionLog;

public interface WorkflowDefinitionLogDao extends IDao<WorkflowDefinitionLog> {

    WorkflowDefinitionLog queryByDefinitionCodeAndVersion(long workflowDefinitionCode, int workflowDefinitionVersion);

    void deleteByWorkflowDefinitionCode(long workflowDefinitionCode);
}
