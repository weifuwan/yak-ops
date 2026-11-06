package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.WorkflowTaskRelation;

import java.util.List;
import java.util.Set;

public interface TaskDefinitionLogDao extends IDao<TaskDefinitionLog> {

    List<TaskDefinitionLog> queryByWorkflowDefinitionCodeAndVersion(Long workflowDefinitionCode,
                                                                    Integer workflowDefinitionVersion);

    List<TaskDefinitionLog> queryTaskDefineLogList(List<WorkflowTaskRelation> workflowTaskRelations);

    void deleteByTaskDefinitionCodes(Set<Long> taskDefinitionCodes);

    List<TaskDefinitionLog> queryVersions(long taskCode);
}
