package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.model.DataSyncTaskWorkflowRow;
import io.yak.ops.dao.model.WorkflowDefinitionCountDto;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TaskDefinitionMapperTest extends BaseDaoTest {

    @Autowired
    private TaskDefinitionMapper taskDefinitionMapper;

    @Autowired
    private WorkflowTaskRelationMapper workflowTaskRelationMapper;

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Autowired
    private UserMapper userMapper;

    public TaskDefinition insertOne() {
        return insertOne(99);
    }

    public TaskDefinition insertOne(int userId) {
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setCode(888888L);
        taskDefinition.setName("unit-test");
        taskDefinition.setProjectCode(1L);
        taskDefinition.setTaskType("SHELL");
        taskDefinition.setUserId(userId);
        taskDefinition.setResourceIds("1");
        taskDefinition.setWorkerGroup("default");
        taskDefinition.setEnvironmentCode(1L);
        taskDefinition.setVersion(1);
        taskDefinition.setCreateTime(new Date());
        taskDefinition.setUpdateTime(new Date());
        taskDefinitionMapper.insert(taskDefinition);
        return taskDefinition;
    }

    /**
     * insert
     *
     * @return ProcessDefinition
     */
    private WorkflowTaskRelation insertTaskRelation(long postTaskCode) {
        WorkflowTaskRelation workflowTaskRelation = new WorkflowTaskRelation();
        workflowTaskRelation.setName("def 1");
        workflowTaskRelation.setProjectCode(1L);
        workflowTaskRelation.setWorkflowDefinitionCode(1L);
        workflowTaskRelation.setPostTaskCode(postTaskCode);
        workflowTaskRelation.setPreTaskCode(0L);
        workflowTaskRelation.setUpdateTime(new Date());
        workflowTaskRelation.setCreateTime(new Date());
        workflowTaskRelationMapper.insert(workflowTaskRelation);
        return workflowTaskRelation;
    }

    @Test
    public void testInsert() {
        TaskDefinition taskDefinition = insertOne();
        Assertions.assertNotEquals(0, taskDefinition.getId().intValue());
    }

    @Test
    public void testQueryByDefinitionName() {
        TaskDefinition taskDefinition = insertOne();
        WorkflowTaskRelation workflowTaskRelation = insertTaskRelation(taskDefinition.getCode());
        TaskDefinition result = taskDefinitionMapper.queryByName(taskDefinition.getProjectCode(),
                workflowTaskRelation.getWorkflowDefinitionCode(), taskDefinition.getName());

        Assertions.assertNotNull(result);
    }

    @Test
    public void testQueryByDefinitionCode() {
        TaskDefinition taskDefinition = insertOne();
        TaskDefinition result = taskDefinitionMapper.queryByCode(taskDefinition.getCode());
        Assertions.assertNotNull(result);

    }

    @Test
    public void testQueryDataSyncWorkflowCodesReturnsProjectScopedSingleTaskWorkflow() {
        long taskCode = 889991L;
        long workflowCode = 889992L;
        Date now = new Date();

        TaskDefinition task = new TaskDefinition();
        task.setCode(taskCode);
        task.setName("data_sync_test");
        task.setProjectCode(1L);
        task.setTaskType("DATA_SYNC");
        task.setFlag(Flag.YES);
        task.setUserId(99);
        task.setResourceIds("1");
        task.setWorkerGroup("default");
        task.setEnvironmentCode(1L);
        task.setVersion(1);
        task.setCreateTime(now);
        task.setUpdateTime(now);

        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setCode(workflowCode);
        workflow.setName("data_sync_" + taskCode);
        workflow.setProjectCode(1L);
        workflow.setFlag(Flag.YES);
        workflow.setReleaseState(ReleaseState.OFFLINE);
        workflow.setVersion(1);
        workflow.setUserId(99);
        workflow.setCreateTime(now);
        workflow.setUpdateTime(now);

        WorkflowTaskRelation relation = new WorkflowTaskRelation();
        relation.setName("data sync relation");
        relation.setProjectCode(1L);
        relation.setWorkflowDefinitionCode(workflowCode);
        relation.setWorkflowDefinitionVersion(1);
        relation.setPreTaskCode(0L);
        relation.setPreTaskVersion(0);
        relation.setPostTaskCode(taskCode);
        relation.setPostTaskVersion(1);
        relation.setCreateTime(now);
        relation.setUpdateTime(now);

        taskDefinitionMapper.insert(task);
        workflowDefinitionMapper.insert(workflow);
        workflowTaskRelationMapper.insert(relation);
        try {
            List<DataSyncTaskWorkflowRow> result = taskDefinitionMapper.queryDataSyncWorkflowCodes(
                    1L, "DATA_SYNC", Collections.singletonList(taskCode));
            Assertions.assertEquals(1, result.size());
            Assertions.assertEquals(taskCode, result.get(0).getTaskCode());
            Assertions.assertEquals(workflowCode, result.get(0).getWorkflowCode());
        } finally {
            workflowTaskRelationMapper.deleteByWorkflowDefinitionCode(1L, workflowCode);
            workflowDefinitionMapper.deleteByCode(workflowCode);
            taskDefinitionMapper.deleteByCode(taskCode);
        }
    }

    @Test
    public void testQueryAllTaskDefinitionWorkerGroups() {
        TaskDefinition taskDefinition = insertOne();
        List<String> taskDefinitions =
                taskDefinitionMapper.queryAllTaskDefinitionWorkerGroups(taskDefinition.getProjectCode());
        Assertions.assertNotEquals(0, taskDefinitions.size());

    }

    @Test
    public void testCountDefinitionGroupByUser() {
        User user = new User();
        user.setUserName("un");
        userMapper.insert(user);
        User un = userMapper.queryByUserNameAccurately("un");
        TaskDefinition taskDefinition = insertOne(un.getId());

        List<WorkflowDefinitionCountDto> users =
                taskDefinitionMapper.countDefinitionGroupByUser(new Long[]{taskDefinition.getProjectCode()});
        Assertions.assertNotEquals(0, users.size());

    }

    @Test
    public void testDeleteByCode() {
        TaskDefinition taskDefinition = insertOne();
        int i = taskDefinitionMapper.deleteByCode(taskDefinition.getCode());
        Assertions.assertNotEquals(0, i);

    }

    @Test
    public void testNullPropertyValueOfLocalParams() {
        String definitionJson =
                "{\"failRetryTimes\":\"0\",\"timeoutNotifyStrategy\":\"\",\"code\":\"5195043558720\",\"flag\":\"YES\",\"environmentCode\":\"-1\",\"taskDefinitionIndex\":2,\"taskPriority\":\"MEDIUM\",\"taskParams\":\"{\\\"preStatements\\\":null,\\\"postStatements\\\":null,\\\"type\\\":\\\"ADB_MYSQL\\\",\\\"database\\\":\\\"lijia\\\",\\\"sql\\\":\\\"create table nation_${random_serial_number} as select * from nation\\\",\\\"localParams\\\":[{\\\"direct\\\":2,\\\"type\\\":3,\\\"prop\\\":\\\"key\\\"}],\\\"Name\\\":\\\"create_table_as_select_nation\\\",\\\"FailRetryTimes\\\":0,\\\"dbClusterId\\\":\\\"amv-bp10o45925jpx959\\\",\\\"sendEmail\\\":false,\\\"displayRows\\\":10,\\\"limit\\\":10000,\\\"agentSource\\\":\\\"Workflow\\\",\\\"agentVersion\\\":\\\"Unkown\\\"}\",\"timeout\":\"0\",\"taskType\":\"ADB_MYSQL\",\"timeoutFlag\":\"CLOSE\",\"projectCode\":\"5191800302720\",\"name\":\"create_table_as_select_nation\",\"delayTime\":\"0\",\"workerGroup\":\"default\"}";
        TaskDefinition definition = JSONUtils.parseObject(definitionJson, TaskDefinition.class);

        Map<String, String> taskParamsMap = definition.getTaskParamMap();
        if (taskParamsMap != null) {
            Assertions.assertNull(taskParamsMap.get("key"));
        } else {
            Assertions.fail("Deserialize the task definition failed");
        }

        String newDefinitionJson = JSONUtils.toJsonString(definition);
        Assertions.assertNotNull("Serialize the task definition success", newDefinitionJson);
    }

    @Test
    public void testNullLocalParamsOfTaskParams() {
        String definitionJson =
                "{\"failRetryTimes\":\"0\",\"timeoutNotifyStrategy\":\"\",\"code\":\"5195043558720\",\"flag\":\"YES\",\"environmentCode\":\"-1\",\"taskDefinitionIndex\":2,\"taskPriority\":\"MEDIUM\",\"taskParams\":\"{\\\"preStatements\\\":null,\\\"postStatements\\\":null,\\\"type\\\":\\\"ADB_MYSQL\\\",\\\"database\\\":\\\"lijia\\\",\\\"sql\\\":\\\"create table nation_${random_serial_number} as select * from nation\\\",\\\"localParams\\\":null,\\\"Name\\\":\\\"create_table_as_select_nation\\\",\\\"FailRetryTimes\\\":0,\\\"dbClusterId\\\":\\\"amv-bp10o45925jpx959\\\",\\\"sendEmail\\\":false,\\\"displayRows\\\":10,\\\"limit\\\":10000,\\\"agentSource\\\":\\\"Workflow\\\",\\\"agentVersion\\\":\\\"Unkown\\\"}\",\"timeout\":\"0\",\"taskType\":\"ADB_MYSQL\",\"timeoutFlag\":\"CLOSE\",\"projectCode\":\"5191800302720\",\"name\":\"create_table_as_select_nation\",\"delayTime\":\"0\",\"workerGroup\":\"default\"}";
        TaskDefinition definition = JSONUtils.parseObject(definitionJson, TaskDefinition.class);

        Assertions.assertNull(definition.getTaskParamMap(), "Serialize the task definition success");
    }
}
