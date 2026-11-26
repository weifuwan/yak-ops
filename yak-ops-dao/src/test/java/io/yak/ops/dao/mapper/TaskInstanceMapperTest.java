package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.model.TaskInstanceStatusCountDto;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;

public class TaskInstanceMapperTest extends BaseDaoTest {

    @Autowired
    private TaskInstanceMapper taskInstanceMapper;

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Autowired
    private WorkflowInstanceMapper workflowInstanceMapper;

    /**
     * insert
     *
     * @return TaskInstance
     */
    private TaskInstance insertTaskInstance(int processInstanceId) {
        // insertOne
        return insertTaskInstance(processInstanceId, "SHELL");
    }

    /**
     * insert
     *
     * @return ProcessInstance
     */
    private WorkflowInstance insertProcessInstance() {
        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setId(1);
        workflowInstance.setName("taskName");
        workflowInstance.setState(WorkflowExecutionStatus.RUNNING_EXECUTION);
        workflowInstance.setStartTime(new Date());
        workflowInstance.setEndTime(new Date());
        workflowInstance.setWorkflowDefinitionCode(1L);
        workflowInstance.setProjectCode(1L);
        workflowInstanceMapper.insert(workflowInstance);
        return workflowInstance;
    }

    /**
     * construct a task instance and then insert
     */
    private TaskInstance insertTaskInstance(int processInstanceId, String taskType) {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setFlag(Flag.YES);
        taskInstance.setName("us task");
        taskInstance.setState(TaskExecutionStatus.RUNNING_EXECUTION);
        taskInstance.setSubmitTime(new Date());
        taskInstance.setStartTime(new Date());
        taskInstance.setEndTime(new Date());
        taskInstance.setWorkflowInstanceId(processInstanceId);
        taskInstance.setProjectCode(1L);
        taskInstance.setTaskType(taskType);
        taskInstanceMapper.insert(taskInstance);
        return taskInstance;
    }

    private TaskInstance insertDataSyncTaskInstance(int workflowInstanceId, long taskCode,
                                                        TaskExecutionStatus state, Flag flag) {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setFlag(flag);
        taskInstance.setName("data-sync-" + taskCode);
        taskInstance.setState(state);
        taskInstance.setSubmitTime(new Date());
        taskInstance.setFirstSubmitTime(new Date());
        taskInstance.setWorkflowInstanceId(workflowInstanceId);
        taskInstance.setProjectCode(1L);
        taskInstance.setTaskType("DATA_SYNC");
        taskInstance.setTaskCode(taskCode);
        taskInstance.setTaskDefinitionVersion(1);
        taskInstanceMapper.insert(taskInstance);
        return taskInstance;
    }

    @Test
    public void testQueryLatestByTaskCodesAppliesStateFilterAfterSelectingLatestValidInstance() {
        WorkflowInstance workflowInstance = insertProcessInstance();
        long taskCode = 990001L;
        TaskInstance earlierRunning = insertDataSyncTaskInstance(
                workflowInstance.getId(), taskCode, TaskExecutionStatus.RUNNING_EXECUTION, Flag.YES);
        TaskInstance latestStopped = insertDataSyncTaskInstance(
                workflowInstance.getId(), taskCode, TaskExecutionStatus.KILL, Flag.YES);
        TaskInstance invalidRunning = insertDataSyncTaskInstance(
                workflowInstance.getId(), taskCode, TaskExecutionStatus.RUNNING_EXECUTION, Flag.NO);

        try {
            int[] activeStates = {
                    TaskExecutionStatus.SUBMITTED_SUCCESS.getCode(),
                    TaskExecutionStatus.DISPATCH.getCode(),
                    TaskExecutionStatus.RUNNING_EXECUTION.getCode(),
                    TaskExecutionStatus.DELAY_EXECUTION.getCode(),
                    TaskExecutionStatus.NEED_FAULT_TOLERANCE.getCode()
            };
            List<TaskInstance> activeInstances = taskInstanceMapper.queryLatestByTaskCodes(
                    1L, "DATA_SYNC", Collections.singletonList(taskCode), activeStates);
            List<TaskInstance> latestInstances = taskInstanceMapper.queryLatestByTaskCodes(
                    1L, "DATA_SYNC", Collections.singletonList(taskCode), null);

            Assertions.assertTrue(activeInstances.isEmpty());
            Assertions.assertEquals(1, latestInstances.size());
            Assertions.assertEquals(latestStopped.getId(), latestInstances.get(0).getId());
            Assertions.assertNotEquals(earlierRunning.getId(), latestInstances.get(0).getId());
        } finally {
            taskInstanceMapper.deleteById(invalidRunning.getId());
            taskInstanceMapper.deleteById(latestStopped.getId());
            taskInstanceMapper.deleteById(earlierRunning.getId());
            workflowInstanceMapper.deleteById(workflowInstance.getId());
        }
    }

    @Test
    public void testQueryByProjectAndTaskTypeOnlyReturnsValidDataSyncInstances() {
        WorkflowInstance workflowInstance = insertProcessInstance();
        long taskCode = 990002L;
        TaskInstance validInstance = insertDataSyncTaskInstance(
                workflowInstance.getId(), taskCode, TaskExecutionStatus.KILL, Flag.YES);
        TaskInstance invalidInstance = insertDataSyncTaskInstance(
                workflowInstance.getId(), taskCode, TaskExecutionStatus.NEED_FAULT_TOLERANCE, Flag.NO);

        try {
            IPage<TaskInstance> page = taskInstanceMapper.queryByProjectAndTaskType(
                    new Page<>(1, 10), 1L, "DATA_SYNC", taskCode, null, null);

            Assertions.assertEquals(1, page.getTotal());
            Assertions.assertEquals(1, page.getRecords().size());
            Assertions.assertEquals(validInstance.getId(), page.getRecords().get(0).getId());
            Assertions.assertEquals(Flag.YES, page.getRecords().get(0).getFlag());
        } finally {
            taskInstanceMapper.deleteById(invalidInstance.getId());
            taskInstanceMapper.deleteById(validInstance.getId());
            workflowInstanceMapper.deleteById(workflowInstance.getId());
        }
    }

    /**
     * test update
     */
    @Test
    public void testUpdate() {
        // insert ProcessInstance
        WorkflowInstance workflowInstance = insertProcessInstance();

        // insert taskInstance
        TaskInstance taskInstance = insertTaskInstance(workflowInstance.getId());
        // update
        int update = taskInstanceMapper.updateById(taskInstance);
        Assertions.assertEquals(1, update);
        taskInstanceMapper.deleteById(taskInstance.getId());
    }

    /**
     * test delete
     */
    @Test
    public void testDelete() {
        // insert ProcessInstance
        WorkflowInstance workflowInstance = insertProcessInstance();

        // insert taskInstance
        TaskInstance taskInstance = insertTaskInstance(workflowInstance.getId());

        int delete = taskInstanceMapper.deleteById(taskInstance.getId());
        Assertions.assertEquals(1, delete);
    }

    /**
     * test query
     */
    @Test
    public void testQuery() {
        // insert ProcessInstance
        WorkflowInstance workflowInstance = insertProcessInstance();

        // insert taskInstance
        TaskInstance taskInstance = insertTaskInstance(workflowInstance.getId());
        // query
        List<TaskInstance> taskInstances = taskInstanceMapper.selectList(null);
        taskInstanceMapper.deleteById(taskInstance.getId());
        Assertions.assertNotEquals(0, taskInstances.size());
    }

    /**
     * test find valid task list by process instance id
     */
    @Test
    public void testFindValidTaskListByWorkflowInstanceId() {
        // insert ProcessInstance
        WorkflowInstance workflowInstance = insertProcessInstance();

        // insert taskInstance
        TaskInstance task = insertTaskInstance(workflowInstance.getId());
        TaskInstance task2 = insertTaskInstance(workflowInstance.getId());
        task.setWorkflowInstanceId(workflowInstance.getId());
        task2.setWorkflowInstanceId(workflowInstance.getId());
        taskInstanceMapper.updateById(task);
        taskInstanceMapper.updateById(task2);

        List<TaskInstance> taskInstances = taskInstanceMapper.findValidTaskListByWorkflowInstanceId(
                task.getWorkflowInstanceId(),
                Flag.YES);

        task2.setFlag(Flag.NO);
        taskInstanceMapper.updateById(task2);
        List<TaskInstance> taskInstances1 =
                taskInstanceMapper.findValidTaskListByWorkflowInstanceId(task.getWorkflowInstanceId(),
                        Flag.NO);
        taskInstanceMapper.deleteById(task2.getId());
        taskInstanceMapper.deleteById(task.getId());
        Assertions.assertNotEquals(0, taskInstances.size());
        Assertions.assertNotEquals(0, taskInstances1.size());
    }

    /**
     * test query by task instance id and code
     */
    @Test
    public void testQueryByInstanceIdAndCode() {
        // insert ProcessInstance
        WorkflowInstance workflowInstance = insertProcessInstance();

        // insert taskInstance
        TaskInstance task = insertTaskInstance(workflowInstance.getId());
        task.setHost("111.111.11.11");
        taskInstanceMapper.updateById(task);

        TaskInstance taskInstance = taskInstanceMapper.queryByInstanceIdAndCode(
                task.getWorkflowInstanceId(),
                task.getTaskCode());
        taskInstanceMapper.deleteById(task.getId());
        Assertions.assertNotEquals(null, taskInstance);
    }

    /**
     * test query by process instance ids and task codes
     */
    @Test
    public void testQueryByWorkflowInstanceIdsAndTaskCodes() {
        // insert ProcessInstance
        WorkflowInstance workflowInstance = insertProcessInstance();

        // insert taskInstance
        TaskInstance task = insertTaskInstance(workflowInstance.getId());
        task.setHost("111.111.11.11");
        taskInstanceMapper.updateById(task);

        List<TaskInstance> taskInstances = taskInstanceMapper.queryByWorkflowInstanceIdsAndTaskCodes(
                Collections.singletonList(task.getWorkflowInstanceId()),
                Collections.singletonList(task.getTaskCode()));
        taskInstanceMapper.deleteById(task.getId());
        Assertions.assertEquals(1, taskInstances.size());
    }

    /**
     * test count task instance state by user
     */
    @Test
    public void testcountTaskInstanceStateByProjectCodes() {

        // insert taskInstance
        TaskInstance task = insertTaskInstance(1);

        List<TaskInstanceStatusCountDto> taskInstanceStatusCountDtos =
                taskInstanceMapper.countTaskInstanceStateByProjectCodes(
                        null,
                        null,
                        Lists.newArrayList(task.getProjectCode()));

        Assertions.assertEquals(1, taskInstanceStatusCountDtos.size());

        taskInstanceMapper.deleteById(task.getId());
    }

    /**
     * test that task instances with null start_time but valid submit_time are included in time range filter
     */
    @Test
    public void testCountTaskInstanceStateByProjectCodes_withSubmitTimeFilter() {
        // insert a task instance with submit_time set but start_time null (e.g. task not yet started)
        TaskInstance task = new TaskInstance();
        task.setFlag(Flag.YES);
        task.setName("submitted task");
        task.setState(TaskExecutionStatus.SUBMITTED_SUCCESS);
        task.setStartTime(null);
        task.setSubmitTime(new Date());
        task.setWorkflowInstanceId(1);
        task.setProjectCode(1L);
        task.setTaskType("SHELL");
        taskInstanceMapper.insert(task);

        Date filterStart = new Date(System.currentTimeMillis() - 3600_000);
        Date filterEnd = new Date(System.currentTimeMillis() + 3600_000);

        // should find the task via submit_time filter
        List<TaskInstanceStatusCountDto> results =
                taskInstanceMapper.countTaskInstanceStateByProjectCodes(
                        filterStart,
                        filterEnd,
                        Lists.newArrayList(task.getProjectCode()));

        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals(TaskExecutionStatus.SUBMITTED_SUCCESS, results.get(0).getState());

        // time range before submit_time should return empty
        Date beforeSubmit = new Date(System.currentTimeMillis() - 7200_000);
        List<TaskInstanceStatusCountDto> emptyResults =
                taskInstanceMapper.countTaskInstanceStateByProjectCodes(
                        beforeSubmit,
                        filterStart,
                        Lists.newArrayList(task.getProjectCode()));

        Assertions.assertTrue(emptyResults.isEmpty());

        taskInstanceMapper.deleteById(task.getId());
    }

    /**
     * test page
     */
    @Test
    public void testQueryTaskInstanceListPaging() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setCode(1L);
        definition.setProjectCode(1111L);
        definition.setCreateTime(new Date());
        definition.setUpdateTime(new Date());
        workflowDefinitionMapper.insert(definition);

        // insert ProcessInstance
        WorkflowInstance workflowInstance = insertProcessInstance();

        // insert taskInstance
        TaskInstance task = insertTaskInstance(workflowInstance.getId());

        Page<TaskInstance> page = new Page(1, 3);
        IPage<TaskInstance> taskInstanceIPage = taskInstanceMapper.queryTaskInstanceListPaging(
                page,
                definition.getProjectCode(),
                task.getWorkflowInstanceId(),
                "",
                "",
                "",
                null,
                "",
                new int[0],
                "",
                TaskExecuteType.BATCH,
                null, null);
        workflowInstanceMapper.deleteById(workflowInstance.getId());
        taskInstanceMapper.deleteById(task.getId());
        workflowDefinitionMapper.deleteById(definition.getId());
        Assertions.assertEquals(0, taskInstanceIPage.getTotal());

    }
}
