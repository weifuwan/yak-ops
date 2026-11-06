package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.WorkflowTaskRelation;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Set;

public interface TaskDefinitionLogDao extends IDao<TaskDefinitionLog> {

    List<TaskDefinitionLog> queryByWorkflowDefinitionCodeAndVersion(Long workflowDefinitionCode,
                                                                    Integer workflowDefinitionVersion);

    List<TaskDefinitionLog> queryTaskDefineLogList(List<WorkflowTaskRelation> workflowTaskRelations);

    void deleteByTaskDefinitionCodes(Set<Long> taskDefinitionCodes);

    List<TaskDefinitionLog> queryVersions(long taskCode);

    IPage<TaskDefinitionLog> queryVersionsPaging(Page<TaskDefinitionLog> page, long taskCode, long projectCode);
}
