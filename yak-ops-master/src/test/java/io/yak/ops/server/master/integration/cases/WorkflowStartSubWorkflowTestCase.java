package io.yak.ops.server.master.integration.cases;

import static com.google.common.truth.Truth.assertThat;
import static org.awaitility.Awaitility.await;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.master.command.RunWorkflowCommandParam;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.AbstractMasterIntegrationTestCase;
import io.yak.ops.server.master.integration.WorkflowOperator;
import io.yak.ops.server.master.integration.WorkflowTestCaseContext;

import java.time.Duration;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for workflow start sub workflow scenarios.
 */
public class WorkflowStartSubWorkflowTestCase extends AbstractMasterIntegrationTestCase {

    @Test
    @DisplayName("Test start a workflow with one sub workflow task(A) success")
    public void testStartWorkflow_with_subWorkflowTask_success() {
        final String yaml = "/it/start/workflow_with_sub_workflow_task_success.yaml";
        final WorkflowTestCaseContext context = workflowTestCaseContextFactory.initializeContextFromYaml(yaml);
        final WorkflowDefinition parentWorkflow = context.getOneWorkflow();

        final WorkflowOperator.WorkflowTriggerDTO workflowTriggerDTO = WorkflowOperator.WorkflowTriggerDTO.builder()
                .workflowDefinition(parentWorkflow)
                .runWorkflowCommandParam(new RunWorkflowCommandParam())
                .build();
        final Integer workflowInstanceId = workflowOperator.manualTriggerWorkflow(workflowTriggerDTO);

        await()
                .atMost(Duration.ofMinutes(1))
                .untilAsserted(() -> {

                    Assertions
                            .assertThat(repository.queryWorkflowInstance(workflowInstanceId))
                            .matches(
                                    workflowInstance -> workflowInstance.getState() == WorkflowExecutionStatus.SUCCESS)
                            .matches(
                                    workflowInstance -> workflowInstance.getIsSubWorkflow() == Flag.NO)
                            .matches(
                                    workflowInstance -> workflowInstance.getDryRun() == Flag.NO.getCode());

                    final List<WorkflowInstance> subWorkflowInstance =
                            repository.queryWorkflowInstance(context.getWorkflows().get(1));
                    Assertions
                            .assertThat(subWorkflowInstance)
                            .hasSize(1)
                            .satisfiesExactly(workflowInstance -> {
                                assertThat(workflowInstance.getState()).isEqualTo(WorkflowExecutionStatus.SUCCESS);
                                assertThat(workflowInstance.getIsSubWorkflow()).isEqualTo(Flag.YES);
                                assertThat(workflowInstance.getDryRun()).isEqualTo(Flag.NO.getCode());
                            });

                    Assertions
                            .assertThat(repository.queryTaskInstance(workflowInstanceId))
                            .satisfiesExactly(taskInstance -> {
                                assertThat(taskInstance.getName()).isEqualTo("sub_logic_task");
                                assertThat(taskInstance.getState()).isEqualTo(TaskExecutionStatus.SUCCESS);
                            });

                    Assertions
                            .assertThat(repository.queryTaskInstance(subWorkflowInstance.get(0).getId()))
                            .satisfiesExactly(taskInstance -> {
                                assertThat(taskInstance.getName()).isEqualTo("fake_task");
                                assertThat(taskInstance.getState()).isEqualTo(TaskExecutionStatus.SUCCESS);
                            });
                });

        masterContainer.assertAllResourceReleased();
    }

    @Test
    @DisplayName("Test start a workflow with one sub workflow task(A) dry run, will not execute")
    public void testStartWorkflow_with_subWorkflowTask_dryRunSuccess() {
        final String yaml = "/it/start/workflow_with_sub_workflow_task_success.yaml";
        final WorkflowTestCaseContext context = workflowTestCaseContextFactory.initializeContextFromYaml(yaml);
        final WorkflowDefinition parentWorkflow = context.getOneWorkflow();

        final WorkflowOperator.WorkflowTriggerDTO workflowTriggerDTO = WorkflowOperator.WorkflowTriggerDTO.builder()
                .workflowDefinition(parentWorkflow)
                .runWorkflowCommandParam(new RunWorkflowCommandParam())
                .dryRun(Flag.YES)
                .build();
        final Integer workflowInstanceId = workflowOperator.manualTriggerWorkflow(workflowTriggerDTO);

        await()
                .atMost(Duration.ofMinutes(1))
                .untilAsserted(() -> {

                    Assertions
                            .assertThat(repository.queryWorkflowInstance(workflowInstanceId))
                            .matches(
                                    workflowInstance -> workflowInstance.getState() == WorkflowExecutionStatus.SUCCESS)
                            .matches(
                                    workflowInstance -> workflowInstance.getIsSubWorkflow() == Flag.NO)
                            .matches(
                                    workflowInstance -> workflowInstance.getDryRun() == Flag.YES.getCode());

                    final List<WorkflowInstance> subWorkflowInstance =
                            repository.queryWorkflowInstance(context.getWorkflows().get(1));
                    Assertions
                            .assertThat(subWorkflowInstance)
                            .isEmpty();

                    Assertions
                            .assertThat(repository.queryTaskInstance(workflowInstanceId))
                            .satisfiesExactly(taskInstance -> {
                                assertThat(taskInstance.getName()).isEqualTo("sub_logic_task");
                                assertThat(taskInstance.getState()).isEqualTo(TaskExecutionStatus.SUCCESS);
                                assertThat(taskInstance.getDryRun()).isEqualTo(Flag.YES.getCode());
                            });
                });
        masterContainer.assertAllResourceReleased();
    }

    @Test
    @DisplayName("Test start a workflow with one sub workflow task(A) failed")
    public void testStartWorkflow_with_subWorkflowTask_failed() {
        final String yaml = "/it/start/workflow_with_sub_workflow_task_failed.yaml";
        final WorkflowTestCaseContext context = workflowTestCaseContextFactory.initializeContextFromYaml(yaml);
        final WorkflowDefinition parentWorkflow = context.getOneWorkflow();

        final WorkflowOperator.WorkflowTriggerDTO workflowTriggerDTO = WorkflowOperator.WorkflowTriggerDTO.builder()
                .workflowDefinition(parentWorkflow)
                .runWorkflowCommandParam(new RunWorkflowCommandParam())
                .build();
        final Integer workflowInstanceId = workflowOperator.manualTriggerWorkflow(workflowTriggerDTO);

        await()
                .atMost(Duration.ofMinutes(1))
                .untilAsserted(() -> {

                    Assertions
                            .assertThat(repository.queryWorkflowInstance(workflowInstanceId))
                            .matches(
                                    workflowInstance -> workflowInstance.getState() == WorkflowExecutionStatus.FAILURE)
                            .matches(
                                    workflowInstance -> workflowInstance.getIsSubWorkflow() == Flag.NO);

                    final List<WorkflowInstance> subWorkflowInstance =
                            repository.queryWorkflowInstance(context.getWorkflows().get(1));
                    Assertions
                            .assertThat(subWorkflowInstance)
                            .hasSize(1)
                            .satisfiesExactly(workflowInstance -> {
                                assertThat(workflowInstance.getState()).isEqualTo(WorkflowExecutionStatus.FAILURE);
                                assertThat(workflowInstance.getIsSubWorkflow()).isEqualTo(Flag.YES);
                            });

                    Assertions
                            .assertThat(repository.queryTaskInstance(workflowInstanceId))
                            .satisfiesExactly(taskInstance -> {
                                assertThat(taskInstance.getName()).isEqualTo("sub_logic_task");
                                assertThat(taskInstance.getState()).isEqualTo(TaskExecutionStatus.FAILURE);
                            });

                    Assertions
                            .assertThat(repository.queryTaskInstance(subWorkflowInstance.get(0).getId()))
                            .satisfiesExactly(taskInstance -> {
                                assertThat(taskInstance.getName()).isEqualTo("fake_task");
                                assertThat(taskInstance.getState()).isEqualTo(TaskExecutionStatus.FAILURE);
                            });
                });

        masterContainer.assertAllResourceReleased();
    }

}
