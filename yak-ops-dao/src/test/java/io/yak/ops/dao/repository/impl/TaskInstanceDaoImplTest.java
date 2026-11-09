package io.yak.ops.dao.repository.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TaskInstanceDaoImplTest extends BaseDaoTest {

    private static final int WORKFLOW_INSTANCE_ID = 1;
    private static final long EXTRACT_TASK = 8001L;
    private static final long TRANSFORM_TASK = 8002L;

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Test
    void queryLastTaskInstanceListIntervalInWorkflowInstance() {
        Date earlier = new Date(System.currentTimeMillis() - 3600_000);
        Date later = new Date();

        insertTaskInstance(EXTRACT_TASK, TaskExecutionStatus.SUCCESS, earlier);
        insertTaskInstance(EXTRACT_TASK, TaskExecutionStatus.SUCCESS, later);
        insertTaskInstance(TRANSFORM_TASK, TaskExecutionStatus.SUCCESS, later);

        Set<Long> taskCodes = new HashSet<>(Arrays.asList(EXTRACT_TASK, TRANSFORM_TASK));
        List<TaskInstance> result = taskInstanceDao.queryLastTaskInstanceListIntervalInWorkflowInstance(
                WORKFLOW_INSTANCE_ID, taskCodes);

        assertEquals(2, result.size());
        TaskInstance extractResult = result.stream()
                .filter(ti -> ti.getTaskCode() == EXTRACT_TASK)
                .findFirst().orElse(null);
        assertNotNull(extractResult);
        assertEquals(later.getTime() / 1000, extractResult.getEndTime().getTime() / 1000);
    }

    @Test
    void queryLastTaskInstanceListIntervalInWorkflowInstanceWhenAttemptsShareTheSameEndTime() {
        Date sameEndTime = new Date();

        insertTaskInstance(EXTRACT_TASK, TaskExecutionStatus.FAILURE, sameEndTime, Flag.NO);
        insertTaskInstance(EXTRACT_TASK, TaskExecutionStatus.SUCCESS, sameEndTime, Flag.YES);

        Set<Long> taskCodes = new HashSet<>(Collections.singletonList(EXTRACT_TASK));
        List<TaskInstance> result = taskInstanceDao.queryLastTaskInstanceListIntervalInWorkflowInstance(
                WORKFLOW_INSTANCE_ID, taskCodes);

        assertEquals(1, result.size());
        assertEquals(TaskExecutionStatus.SUCCESS, result.get(0).getState());
    }

    @Test
    void queryLastTaskInstanceIntervalInWorkflowInstance() {
        Date earlier = new Date(System.currentTimeMillis() - 3600_000);
        Date later = new Date();

        insertTaskInstance(EXTRACT_TASK, TaskExecutionStatus.SUCCESS, earlier);
        insertTaskInstance(EXTRACT_TASK, TaskExecutionStatus.SUCCESS, later);

        TaskInstance result = taskInstanceDao.queryLastTaskInstanceIntervalInWorkflowInstance(
                WORKFLOW_INSTANCE_ID, EXTRACT_TASK);
        assertNotNull(result);
        assertEquals(later.getTime() / 1000, result.getEndTime().getTime() / 1000);
    }

    private void insertTaskInstance(long taskCode, TaskExecutionStatus state, Date endTime) {
        insertTaskInstance(taskCode, state, endTime, Flag.YES);
    }

    private void insertTaskInstance(long taskCode, TaskExecutionStatus state, Date endTime, Flag flag) {
        TaskInstance ti = TaskInstance.builder()
                .name("shell-task-" + taskCode)
                .taskType("SHELL")
                .workflowInstanceId(WORKFLOW_INSTANCE_ID)
                .workflowInstanceName("daily-etl-pipeline")
                .taskCode(taskCode)
                .taskDefinitionVersion(1)
                .state(state)
                .flag(flag)
                .submitTime(new Date())
                .firstSubmitTime(new Date())
                .startTime(new Date())
                .endTime(endTime)
                .host("192.168.1.50:5678")
                .executePath("/tmp/yak-ops/exec/" + WORKFLOW_INSTANCE_ID + "/" + taskCode)
                .logPath("/tmp/yak-ops/logs/" + WORKFLOW_INSTANCE_ID + "/" + taskCode + ".log")
                .build();
        taskInstanceDao.upsertTaskInstance(ti);
    }
}
