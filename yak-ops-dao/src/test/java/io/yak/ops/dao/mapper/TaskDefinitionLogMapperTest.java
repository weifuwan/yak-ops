package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TaskDefinitionLogMapperTest extends BaseDaoTest {

    @Autowired
    private TaskDefinitionLogMapper taskDefinitionLogMapper;

    public TaskDefinitionLog insertOne() {
        return insertOne(99);
    }

    public TaskDefinitionLog insertOne(int userId) {
        TaskDefinitionLog taskDefinition = new TaskDefinitionLog();
        taskDefinition.setCode(888888L);
        taskDefinition.setName("unit-test");
        taskDefinition.setProjectCode(1L);
        taskDefinition.setTaskType("SHELL");
        taskDefinition.setUserId(userId);
        taskDefinition.setEnvironmentCode(1L);
        taskDefinition.setWorkerGroup("default");
        taskDefinition.setVersion(1);
        taskDefinition.setCreateTime(new Date());
        taskDefinition.setUpdateTime(new Date());
        taskDefinitionLogMapper.insert(taskDefinition);
        return taskDefinition;
    }

    @Test
    public void testInsert() {
        TaskDefinitionLog taskDefinitionLog = insertOne();
        Assertions.assertNotEquals(0, taskDefinitionLog.getId().intValue());
    }

    @Test
    public void testQueryMaxVersionForDefinition() {
        TaskDefinitionLog taskDefinitionLog = insertOne();
        int version = taskDefinitionLogMapper
                .queryMaxVersionForDefinition(taskDefinitionLog.getCode());
        Assertions.assertNotEquals(0, version);
    }

    @Test
    public void testQueryByDefinitionCodeAndVersion() {
        TaskDefinitionLog taskDefinitionLog = insertOne();
        TaskDefinitionLog tdl = taskDefinitionLogMapper
                .queryByDefinitionCodeAndVersion(taskDefinitionLog.getCode(), taskDefinitionLog.getVersion());
        Assertions.assertNotNull(tdl);
    }

    @Test
    public void testQueryByTaskDefinitions() {
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setCode(888888L);
        taskDefinition.setName("unit-test");
        taskDefinition.setProjectCode(1L);
        taskDefinition.setTaskType("SHELL");
        taskDefinition.setUserId(1);
        taskDefinition.setResourceIds("1");
        taskDefinition.setVersion(1);
        ArrayList<TaskDefinition> taskDefinitions = new ArrayList<>();
        taskDefinitions.add(taskDefinition);

        TaskDefinitionLog taskDefinitionLog = insertOne();
        List<TaskDefinitionLog> taskDefinitionLogs = taskDefinitionLogMapper.queryByTaskDefinitions(taskDefinitions);
        Assertions.assertNotEquals(0, taskDefinitionLogs.size());
    }

}
