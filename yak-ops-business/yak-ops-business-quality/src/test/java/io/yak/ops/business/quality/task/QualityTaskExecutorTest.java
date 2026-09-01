package io.yak.ops.business.quality.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.execution.QualityExecutionDefinition;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.MonitorSnapshot;
import io.yak.ops.business.quality.execution.QualityExecutionManager;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.execution.QualityExecutionReceipt;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QualityTaskExecutorTest {

  @Test
  void workflowStartUsesPinnedExecutionDefinition() throws Exception {
    QualityExecutionManager manager = Mockito.mock(QualityExecutionManager.class);
    QualityExecutionReader reader = Mockito.mock(QualityExecutionReader.class);
    ObjectMapper objectMapper = new ObjectMapper();
    QualityTaskExecutor executor = new QualityTaskExecutor(manager, reader, objectMapper);
    QualityExecutionDefinition definition = definition();
    String json = objectMapper.writeValueAsString(definition);
    when(manager.runWorkflowSnapshot(eq(definition), eq("workflow"), eq("attempt-1")))
        .thenReturn(new QualityExecutionReceipt(
            "QM-1", ExecutionStatus.WAITING, CheckResult.RUNNING));
    when(reader.requireSummary("QM-1")).thenReturn(execution(
        ExecutionStatus.WAITING, CheckResult.RUNNING, 0, 0, 0));

    TaskExecution started = executor.start(
        new TaskVersionSnapshot("asset-1", "quality", "QUALITY", 2L, "digest", null, json),
        "attempt-1",
        Map.of());

    assertThat(started.executionId()).isEqualTo("QM-1");
    assertThat(started.status()).isEqualTo("SUBMITTED");
    verify(manager).runWorkflowSnapshot(definition, "workflow", "attempt-1");
  }

  @Test
  void qualityGateFailureFailsWorkflowTaskEvenWhenExecutionCompleted() {
    QualityExecutionManager manager = Mockito.mock(QualityExecutionManager.class);
    QualityExecutionReader reader = Mockito.mock(QualityExecutionReader.class);
    QualityTaskExecutor executor = new QualityTaskExecutor(manager, reader, new ObjectMapper());
    when(reader.requireSummary("QM-2")).thenReturn(execution(
        ExecutionStatus.SUCCESS, CheckResult.NOT_PASSED, 1, 1, 0));

    TaskExecution result = executor.status("QM-2");

    assertThat(result.status()).isEqualTo("FAILED");
    assertThat(result.errorMessage()).isEqualTo("数据质量检查未通过");
    assertThat(result.output())
        .containsEntry("checkResult", CheckResult.NOT_PASSED)
        .containsEntry("failedRules", 1);
  }

  @Test
  void passedQualityGateSucceedsAndCancelDelegates() {
    QualityExecutionManager manager = Mockito.mock(QualityExecutionManager.class);
    QualityExecutionReader reader = Mockito.mock(QualityExecutionReader.class);
    QualityTaskExecutor executor = new QualityTaskExecutor(manager, reader, new ObjectMapper());
    when(reader.requireSummary("QM-3")).thenReturn(execution(
        ExecutionStatus.SUCCESS, CheckResult.PASSED, 1, 0, 0));

    assertThat(executor.status("QM-3").status()).isEqualTo("SUCCEEDED");
    executor.cancel("QM-3");

    verify(manager).cancelWorkflowExecution("QM-3");
  }

  private QualityExecutionDefinition definition() {
    return new QualityExecutionDefinition(
        7L,
        new MonitorSnapshot(
            42L,
            "customers-quality",
            9L,
            "mysql",
            "sales",
            null,
            "customers",
            null,
            "owner"),
        List.of(),
        RuleFailureAction.CONTINUE,
        false,
        NotifyChannel.MESSAGE,
        null,
        AlertLevel.WARNING);
  }

  private Execution execution(
      ExecutionStatus status,
      CheckResult result,
      int passed,
      int failed,
      int errors) {
    LocalDateTime now = LocalDateTime.now();
    return new Execution(
        1L,
        status == ExecutionStatus.WAITING ? "QM-1" : status == ExecutionStatus.SUCCESS && failed > 0 ? "QM-2" : "QM-3",
        42L,
        "customers-quality",
        9L,
        "mysql",
        "sales",
        null,
        "customers",
        "sales.customers",
        TriggerType.WORKFLOW,
        status,
        result,
        1,
        passed,
        failed,
        errors,
        "workflow",
        now,
        status == ExecutionStatus.WAITING ? null : now,
        status == ExecutionStatus.WAITING ? null : now,
        status == ExecutionStatus.WAITING ? null : 10L,
        null,
        List.of());
  }
}
