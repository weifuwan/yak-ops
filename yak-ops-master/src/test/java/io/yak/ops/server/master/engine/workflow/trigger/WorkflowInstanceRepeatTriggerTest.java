package io.yak.ops.server.master.engine.workflow.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRepeatRunningRequest;

import org.junit.jupiter.api.Test;

class WorkflowInstanceRepeatTriggerTest {

    @Test
    void testRepeatRunningCommandShouldInheritWorkflowInstanceWorkerGroup() {
        final int workflowInstanceId = 1;
        final long workflowDefinitionCode = 1001L;
        final int workflowDefinitionVersion = 2;
        final String workerGroup = "worker-group-a";
        final WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setId(workflowInstanceId);
        workflowInstance.setWorkflowDefinitionCode(workflowDefinitionCode);
        workflowInstance.setWorkflowDefinitionVersion(workflowDefinitionVersion);
        workflowInstance.setWorkerGroup(workerGroup);

        final Command command = new WorkflowInstanceRepeatTrigger().constructTriggerCommand(
                WorkflowInstanceRepeatRunningRequest.builder()
                        .workflowInstanceId(workflowInstanceId)
                        .userId(1)
                        .build(),
                workflowInstance);

        assertThat(command.getCommandType()).isEqualTo(CommandType.REPEAT_RUNNING);
        assertThat(command.getWorkerGroup()).isEqualTo(workerGroup);
    }
}
