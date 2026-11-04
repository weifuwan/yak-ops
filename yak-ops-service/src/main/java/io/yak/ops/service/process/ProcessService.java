package io.yak.ops.service.process;

import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.graph.DAG;
import io.yak.ops.common.model.TaskNodeRelation;
import io.yak.ops.dao.entity.DagData;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowDefinitionLog;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.service.model.TaskNode;

import java.util.List;
import java.util.Optional;

public interface ProcessService {

    Optional<WorkflowInstance> findWorkflowInstanceDetailById(int workflowInstanceId);

    WorkflowInstance findWorkflowInstanceById(int workflowInstanceId);

    WorkflowDefinition findWorkflowDefinition(Long workflowDefinitionCode, int workflowDefinitionVersion);

    int deleteWorkflowInstanceById(int workflowInstanceId);

    List<Long> findAllSubWorkflowDefinitionCode(long workflowDefinitionCode);

    String getTenantForWorkflow(String tenantCode, int userId);

    WorkflowInstance findSubWorkflowInstance(Integer parentWorkflowInstanceId, Integer parentTaskId);

    WorkflowInstance findParentWorkflowInstance(Integer subWorkflowInstanceId);

    List<Schedule> queryReleaseSchedulerListByWorkflowDefinitionCode(long workflowDefinitionCode);

    DataSource findDataSourceById(int id);

    <T> List<T> listUnauthorized(int userId, T[] needChecks, AuthorizationType authorizationType);

    User getUserById(int userId);

    int switchVersion(WorkflowDefinition workflowDefinition, WorkflowDefinitionLog workflowDefinitionLog);

    int switchWorkflowTaskRelationVersion(WorkflowDefinition workflowDefinition);

    int switchTaskDefinitionVersion(long taskCode, int taskVersion);

    int saveTaskDefine(User operator, long projectCode, List<TaskDefinitionLog> taskDefinitionLogs, Boolean syncDefine);

    int saveWorkflowDefine(User operator, WorkflowDefinition workflowDefinition, Boolean syncDefine,
                           Boolean isFromWorkflowDefinition);

    int saveTaskRelation(User operator, long projectCode, long workflowDefinitionCode, int workflowDefinitionVersion,
                         List<WorkflowTaskRelationLog> taskRelationList, List<TaskDefinitionLog> taskDefinitionLogs,
                         Boolean syncDefine);

    boolean isTaskOnline(long taskCode);

    DAG<Long, TaskNode, TaskNodeRelation> genDagGraph(WorkflowDefinition workflowDefinition);

    DagData genDagData(WorkflowDefinition workflowDefinition);

    List<WorkflowTaskRelation> findRelationByCode(long workflowDefinitionCode, int workflowDefinitionVersion);

    List<TaskNode> transformTask(List<WorkflowTaskRelation> taskRelationList,
                                 List<TaskDefinitionLog> taskDefinitionLogs);

    void forceWorkflowInstanceSuccessByTaskInstanceId(TaskInstance taskInstance);

}
