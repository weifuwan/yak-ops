package io.yak.ops.dao.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;

import java.util.Date;

import org.junit.jupiter.api.Test;

class ErrorCommandTest {

    @Test
    void testConstructor() {
        Command command = new Command();
        command.setId(1);
        command.setCommandType(CommandType.PAUSE);
        command.setExecutorId(1);
        command.setWorkflowDefinitionCode(123);
        command.setWorkflowDefinitionVersion(1);
        command.setWorkflowInstanceId(1);
        command.setCommandParam("param");
        command.setTaskDependType(TaskDependType.TASK_POST);
        command.setFailureStrategy(FailureStrategy.CONTINUE);
        command.setWarningType(WarningType.ALL);
        command.setWarningGroupId(1);
        command.setScheduleTime(new Date());
        command.setStartTime(new Date());
        command.setUpdateTime(new Date());
        command.setWorkflowInstancePriority(Priority.HIGHEST);
        command.setWorkerGroup("default");
        command.setTenantCode("root");
        command.setEnvironmentCode(1L);
        command.setDryRun(1);

        ErrorCommand errorCommand = new ErrorCommand(command, "test");
        assertEquals(command.getCommandType(), errorCommand.getCommandType());
        assertEquals(command.getExecutorId(), errorCommand.getExecutorId());
        assertEquals(command.getWorkflowDefinitionCode(), errorCommand.getWorkflowDefinitionCode());
        assertEquals(command.getWorkflowDefinitionVersion(), errorCommand.getWorkflowDefinitionVersion());
        assertEquals(command.getWorkflowInstanceId(), errorCommand.getWorkflowInstanceId());
        assertEquals(command.getCommandParam(), errorCommand.getCommandParam());
        assertEquals(command.getTaskDependType(), errorCommand.getTaskDependType());
        assertEquals(command.getFailureStrategy(), errorCommand.getFailureStrategy());
        assertEquals(command.getWarningType(), errorCommand.getWarningType());
        assertEquals(command.getWarningGroupId(), errorCommand.getWarningGroupId());
        assertEquals(command.getScheduleTime(), errorCommand.getScheduleTime());
        assertEquals(command.getStartTime(), errorCommand.getStartTime());
        assertEquals(command.getUpdateTime(), errorCommand.getUpdateTime());
        assertEquals(command.getWorkflowInstancePriority(), errorCommand.getWorkflowInstancePriority());
        assertEquals(command.getWorkerGroup(), errorCommand.getWorkerGroup());
        assertEquals(command.getTenantCode(), errorCommand.getTenantCode());
        assertEquals(command.getEnvironmentCode(), errorCommand.getEnvironmentCode());
        assertEquals(command.getDryRun(), errorCommand.getDryRun());
        assertEquals("test", errorCommand.getMessage());
    }

}
