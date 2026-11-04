package io.yak.ops.server.master.integration.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.yak.ops.common.constants.SystemConstants;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.mapper.ScheduleMapper;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.AbstractMasterIntegrationTestCase;
import io.yak.ops.server.master.integration.WorkflowOperator;
import io.yak.ops.server.master.integration.WorkflowTestCaseContext;

import org.apache.commons.lang3.time.DateUtils;

import java.time.Duration;
import java.util.Date;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

/**
 * The integration test for scheduling a workflow from workflow definition.
 */
@Slf4j
public class WorkflowSchedulingTestCase extends AbstractMasterIntegrationTestCase {

    @Autowired
    private ScheduleMapper scheduleMapper;

    @Test
    @DisplayName("Test scheduling a workflow with one fake task(A) success")
    public void testSchedulingWorkflow_with_oneSuccessTask() {
        final String yaml = "/it/scheduling/workflow_with_one_fake_task_success.yaml";
        final WorkflowTestCaseContext context = workflowTestCaseContextFactory.initializeContextFromYaml(yaml);
        final WorkflowDefinition workflow = context.getOneWorkflow();

        final Schedule schedule = Schedule.builder()
                .workflowDefinitionCode(workflow.getCode())
                .startTime(new Date())
                .endTime(DateUtils.addDays(new Date(), 1))
                .timezoneId(SystemConstants.DEFAULT_TIME_ZONE.getID())
                .crontab("0/5 * * * * ?")
                .failureStrategy(FailureStrategy.CONTINUE)
                .warningType(WarningType.NONE)
                .createTime(new Date())
                .updateTime(new Date())
                .userId(1)
                .releaseState(ReleaseState.ONLINE)
                .workflowInstancePriority(Priority.MEDIUM)
                .build();

        scheduleMapper.insert(schedule);

        WorkflowOperator.WorkflowSchedulingDTO workflowSchedulingDTO = WorkflowOperator.WorkflowSchedulingDTO.builder()
                .project(context.getProject())
                .workflow(context.getOneWorkflow())
                .schedule(schedule)
                .build();

        workflowOperator.schedulingWorkflow(workflowSchedulingDTO);

        await()
                .atMost(Duration.ofMinutes(1))
                .untilAsserted(() -> {
                    assertThat(repository.queryWorkflowInstance(workflow))
                            .areAtLeast(3,
                                    new Condition<>(instance -> instance.getState() == WorkflowExecutionStatus.SUCCESS,
                                            "Workflow instance should be success"));
                });

        workflowOperator.unSchedulingWorkflow(workflowSchedulingDTO);

        await()
                .atMost(Duration.ofMinutes(1))
                .untilAsserted(() -> {
                    assertThat(repository.queryTaskInstance(workflow))
                            .areAtLeast(3,
                                    new Condition<>(
                                            taskInstance -> taskInstance.getState() == TaskExecutionStatus.SUCCESS,
                                            "Task instance should be A"));
                });

        masterContainer.assertAllResourceReleased();
    }

}
