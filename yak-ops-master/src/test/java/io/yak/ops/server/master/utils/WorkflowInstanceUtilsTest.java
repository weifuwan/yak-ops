package io.yak.ops.server.master.utils;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.utils.WorkerGroupUtils;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.sql.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WorkflowInstanceUtilsTest {

    @Test
    public void testLogWorkflowInstanceInDetails() {
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setName("test_workflow");

        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setWorkflowDefinition(workflowDefinition);
        workflowInstance.setName("test_workflow_20230801");
        workflowInstance.setCommandType(CommandType.REPEAT_RUNNING);
        workflowInstance.setState(WorkflowExecutionStatus.SUCCESS);
        workflowInstance.setHost("127.0.0.1");
        workflowInstance.setIsSubWorkflow(Flag.NO);
        workflowInstance.setRunTimes(1);
        workflowInstance.setMaxTryTimes(0);
        workflowInstance.setScheduleTime(Date.valueOf("2023-08-01"));
        workflowInstance.setDryRun(0);
        workflowInstance.setTenantCode("default");
        workflowInstance.setRestartTime(Date.valueOf("2023-08-01"));
        workflowInstance.setWorkerGroup(WorkerGroupUtils.getDefaultWorkerGroup());
        workflowInstance.setStartTime(Date.valueOf("2023-08-01"));
        workflowInstance.setEndTime(Date.valueOf("2023-08-01"));
        // Assertions.assertEquals("\n"
        // + "********************************************************************************\n"
        // + " Workflow Instance Detail\n"
        // + "********************************************************************************\n"
        // + "Workflow Name: test_workflow\n"
        // + "Workflow Instance Name: test_workflow_20230801\n"
        // + "Command Type: REPEAT_RUNNING\n"
        // + "State: success\n"
        // + "Host: 127.0.0.1\n"
        // + "Is Sub Process: no\n"
        // + "Run Times: 1\n"
        // + "Max Try Times: 0\n"
        // + "Schedule Time: 2023-08-01\n"
        // + "Dry Run: 0\n"
        // + "Tenant: default\n"
        // + "Restart Time: 2023-08-01\n"
        // + "Work Group: default\n"
        // + "Start Time: 2023-08-01\n"
        // + "End Time: 2023-08-01\n",
        // WorkflowInstanceUtils.logWorkflowInstanceInDetails(workflowInstance));
    }

    @Test
    public void testLogTaskInstanceInDetails() {
        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setName("test_process");
        workflowInstance.setTenantCode("default");

        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setName("test_task");
        taskInstance.setWorkflowInstance(workflowInstance);
        taskInstance.setState(TaskExecutionStatus.SUCCESS);
        taskInstance.setTaskExecuteType(TaskExecuteType.BATCH);
        taskInstance.setHost("127.0.0.1");
        taskInstance.setTaskType("SHELL");
        taskInstance.setTaskInstancePriority(Priority.MEDIUM);
        taskInstance.setFirstSubmitTime(Date.valueOf("2023-08-01"));
        taskInstance.setSubmitTime(Date.valueOf("2023-08-01"));
        taskInstance.setStartTime(Date.valueOf("2023-08-01"));
        taskInstance.setEndTime(Date.valueOf("2023-08-01"));
        Assertions.assertEquals("\n"
                + "********************************************************************************\n"
                + "                    Task Instance Detail\n"
                + "********************************************************************************\n"
                + "Task Name:              test_task\n"
                + "Workflow Instance Name: test_process\n"
                + "Task Execute Type:      batch\n"
                + "Execute State:          success\n"
                + "Host:                   127.0.0.1\n"
                + "Task Type:              SHELL\n"
                + "Priority:               medium\n"
                + "Tenant:                 default\n"
                + "First Submit Time:      2023-08-01\n"
                + "Submit Time:            2023-08-01\n"
                + "Start Time:             2023-08-01\n"
                + "End Time:               2023-08-01\n", WorkflowInstanceUtils.logTaskInstanceInDetail(taskInstance));
    }
}
