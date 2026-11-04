package io.yak.ops.api.service;

import io.yak.ops.dao.entity.WorkflowTaskRelation;

import java.util.List;

public interface WorkflowTaskRelationService {

    List<WorkflowTaskRelation> queryByWorkflowDefinitionCode(long workflowDefinitionCode,
                                                             int workflowDefinitionVersion);

    void deleteByWorkflowDefinitionCode(long workflowDefinitionCode, int workflowDefinitionVersion);
}
