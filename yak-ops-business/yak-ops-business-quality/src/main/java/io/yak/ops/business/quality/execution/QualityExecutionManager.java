package io.yak.ops.business.quality.execution;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.execution.QualityExecutionDefinition;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.repository.QualityExecutionRepository;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.business.quality.repository.QualityWorkflowExecutionRepository;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Command-side owner for accepting manual, scheduled and Workflow quality executions. */
@Component
@ConditionalOnQualityEnabled
public class QualityExecutionManager {
  private static final DateTimeFormatter EXECUTION_TIME =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

  private final QualityMonitorRepository monitorRepository;
  private final QualityExecutionRepository executionRepository;
  private final QualityWorkflowExecutionRepository workflowExecutionRepository;
  private final QualityExecutionPlanFactory planFactory;
  private final QualityExecutionDispatcher dispatcher;

  public QualityExecutionManager(
      QualityMonitorRepository monitorRepository,
      QualityExecutionRepository executionRepository,
      QualityWorkflowExecutionRepository workflowExecutionRepository,
      QualityExecutionPlanFactory planFactory,
      QualityExecutionDispatcher dispatcher) {
    this.monitorRepository = monitorRepository;
    this.executionRepository = executionRepository;
    this.workflowExecutionRepository = workflowExecutionRepository;
    this.planFactory = planFactory;
    this.dispatcher = dispatcher;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionReceipt run(long monitorId, String operator) {
    return enqueue(monitorId, operator, TriggerType.MANUAL);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionReceipt runScheduled(long monitorId) {
    return enqueue(monitorId, "quality-scheduler", TriggerType.SCHEDULE);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionReceipt runWorkflowSnapshot(
      QualityExecutionDefinition definition,
      String operator,
      String idempotencyKey) {
    String existingNo = workflowExecutionRepository
        .findExecutionNoByIdempotencyKey(idempotencyKey)
        .orElse(null);
    if (existingNo != null) return receipt(existingNo);
    if (definition.rules().isEmpty()) {
      throw new IllegalStateException("质量任务快照没有可执行规则");
    }
    if (executionRepository.hasActiveExecution(definition.monitor().id())) {
      throw new IllegalStateException("该质量监控已有运行中的检查任务");
    }

    LocalDateTime queuedAt = LocalDateTime.now();
    String executionNo = executionNo(queuedAt);
    long executionId;
    try {
      executionId = workflowExecutionRepository.insert(
          executionNo,
          definition,
          normalizeOperator(operator),
          TriggerType.WORKFLOW,
          queuedAt,
          idempotencyKey);
    } catch (DuplicateKeyException exception) {
      String racedExecutionNo = workflowExecutionRepository
          .findExecutionNoByIdempotencyKey(idempotencyKey)
          .orElseThrow(() -> exception);
      return receipt(racedExecutionNo);
    }
    QualityExecutionPlan plan = planFactory.create(definition, executionId, executionNo);
    dispatcher.dispatchAfterCommit(plan);
    return new QualityExecutionReceipt(
        executionNo, ExecutionStatus.WAITING, CheckResult.RUNNING);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void cancelWorkflowExecution(String executionNo) {
    workflowExecutionRepository.cancel(executionNo, LocalDateTime.now());
  }

  private QualityExecutionReceipt enqueue(
      long monitorId, String operator, TriggerType triggerType) {
    monitorRepository.lockMonitor(monitorId);
    Monitor monitor = monitorRepository.findMonitor(monitorId)
        .orElseThrow(() -> new IllegalArgumentException("质量监控不存在：" + monitorId));
    if (!monitor.enabled()) {
      throw new IllegalStateException("质量监控已停用，无法执行");
    }
    int enabledRules = (int) monitor.rules().stream().filter(rule -> rule.enabled()).count();
    if (enabledRules == 0) {
      throw new IllegalStateException("质量监控没有可执行规则");
    }
    if (executionRepository.hasActiveExecution(monitorId)) {
      throw new IllegalStateException("该质量监控已有运行中的检查任务");
    }

    LocalDateTime queuedAt = LocalDateTime.now();
    String executionNo = executionNo(queuedAt);
    long executionId = executionRepository.insertExecution(
        executionNo, monitor, enabledRules, normalizeOperator(operator), triggerType, queuedAt);
    QualityExecutionPlan plan = planFactory.create(monitor, executionId, executionNo);
    dispatcher.dispatchAfterCommit(plan);
    return new QualityExecutionReceipt(
        executionNo, ExecutionStatus.WAITING, CheckResult.RUNNING);
  }

  private QualityExecutionReceipt receipt(String executionNo) {
    Execution execution = executionRepository.findExecution(executionNo)
        .orElseThrow(() -> new IllegalStateException("质量执行不存在：" + executionNo));
    return new QualityExecutionReceipt(
        execution.executionNo(), execution.executionStatus(), execution.checkResult());
  }

  private static String executionNo(LocalDateTime queuedAt) {
    return "QM-" + EXECUTION_TIME.format(queuedAt) + "-"
        + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
  }

  private static String normalizeOperator(String operator) {
    return operator == null || operator.isBlank() ? "system" : operator.trim();
  }
}
