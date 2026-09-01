package io.yak.ops.business.quality.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.execution.QualityExecutionDefinition;
import io.yak.ops.business.quality.execution.QualityExecutionManager;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.execution.QualityExecutionReceipt;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Adapts immutable Data Quality revisions to the generic Workflow task runtime. */
@Component
@ConditionalOnQualityEnabled
public class QualityTaskExecutor implements TaskExecutor {
  private final QualityExecutionManager executionManager;
  private final QualityExecutionReader executionReader;
  private final ObjectMapper objectMapper;

  public QualityTaskExecutor(
      QualityExecutionManager executionManager,
      QualityExecutionReader executionReader,
      ObjectMapper objectMapper) {
    this.executionManager = executionManager;
    this.executionReader = executionReader;
    this.objectMapper = objectMapper;
  }

  @Override
  public String taskType() {
    return QualityTaskPublisher.TASK_TYPE;
  }

  @Override
  public TaskExecution start(
      TaskVersionSnapshot snapshot,
      String idempotencyKey,
      Map<String, Object> input) {
    requireQualitySnapshot(snapshot);
    QualityExecutionDefinition definition = read(snapshot.executionConfigSnapshotJson());
    QualityExecutionReceipt receipt = executionManager.runWorkflowSnapshot(
        definition,
        "workflow",
        idempotencyKey);
    return toExecution(executionReader.requireSummary(receipt.executionNo()));
  }

  @Override
  public TaskExecution status(String executionId) {
    return toExecution(executionReader.requireSummary(requireExecutionNo(executionId)));
  }

  @Override
  public void cancel(String executionId) {
    executionManager.cancelWorkflowExecution(requireExecutionNo(executionId));
  }

  private TaskExecution toExecution(Execution execution) {
    String status = taskStatus(execution);
    Map<String, Object> output = new LinkedHashMap<>();
    put(output, "executionNo", execution.executionNo());
    put(output, "monitorId", execution.monitorId());
    put(output, "checkResult", execution.checkResult());
    put(output, "totalRules", execution.totalRules());
    put(output, "passedRules", execution.passedRules());
    put(output, "failedRules", execution.failedRules());
    put(output, "errorRules", execution.errorRules());
    put(output, "durationMillis", execution.durationMs());
    return new TaskExecution(
        execution.executionNo(),
        status,
        errorMessage(execution, status),
        output);
  }

  private String taskStatus(Execution execution) {
    return switch (execution.executionStatus()) {
      case WAITING -> "SUBMITTED";
      case RUNNING -> "RUNNING";
      case FAILED -> "FAILED";
      case CANCELED -> "CANCELED";
      case SUCCESS -> execution.checkResult() == CheckResult.PASSED ? "SUCCEEDED" : "FAILED";
    };
  }

  private String errorMessage(Execution execution, String taskStatus) {
    if (execution.errorMessage() != null && !execution.errorMessage().isBlank()) {
      return execution.errorMessage().trim();
    }
    if (!"FAILED".equals(taskStatus)) return null;
    return execution.checkResult() == CheckResult.NOT_PASSED
        ? "数据质量检查未通过"
        : "数据质量检查执行异常";
  }

  private QualityExecutionDefinition read(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("质量任务执行快照不能为空");
    }
    try {
      return objectMapper.readValue(json, QualityExecutionDefinition.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("质量任务执行快照无法解析", exception);
    }
  }

  private void requireQualitySnapshot(TaskVersionSnapshot snapshot) {
    if (snapshot == null) throw new IllegalArgumentException("任务版本快照不能为空");
    if (!QualityTaskPublisher.TASK_TYPE.equalsIgnoreCase(snapshot.type())) {
      throw new IllegalArgumentException("仅支持 QUALITY 任务版本快照：" + snapshot.taskId());
    }
  }

  private String requireExecutionNo(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("质量任务 executionId 不能为空");
    }
    return value.trim();
  }

  private void put(Map<String, Object> target, String key, Object value) {
    if (value != null) target.put(key, value);
  }
}
