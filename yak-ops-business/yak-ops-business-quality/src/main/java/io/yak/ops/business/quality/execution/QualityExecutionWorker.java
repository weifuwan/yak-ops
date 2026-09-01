package io.yak.ops.business.quality.execution;

import io.yak.ops.business.quality.alert.QualityAlertRecorder;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionSpec;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.RuleSnapshot;
import io.yak.ops.business.quality.execution.QualityMetricEvaluator.MetricMeasurement;
import io.yak.ops.business.quality.execution.QualitySqlCompiler.CompiledRule;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway;
import io.yak.ops.business.quality.repository.QualityExecutionRepository;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.business.quality.repository.QualityWorkflowExecutionRepository;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@ConditionalOnQualityEnabled
@Component
public class QualityExecutionWorker {
  private final QualityExecutionRepository executionRepository;
  private final QualityWorkflowExecutionRepository workflowExecutionRepository;
  private final QualityMonitorRepository monitorRepository;
  private final QualitySqlCompiler compiler;
  private final QualityMetricEvaluator evaluator;
  private final QualityDataCatalogGateway catalogGateway;
  private final QualityAlertRecorder alertRecorder;

  public QualityExecutionWorker(
      QualityExecutionRepository executionRepository,
      QualityWorkflowExecutionRepository workflowExecutionRepository,
      QualityMonitorRepository monitorRepository,
      QualitySqlCompiler compiler,
      QualityMetricEvaluator evaluator,
      QualityDataCatalogGateway catalogGateway,
      QualityAlertRecorder alertRecorder) {
    this.executionRepository = executionRepository;
    this.workflowExecutionRepository = workflowExecutionRepository;
    this.monitorRepository = monitorRepository;
    this.compiler = compiler;
    this.evaluator = evaluator;
    this.catalogGateway = catalogGateway;
    this.alertRecorder = alertRecorder;
  }

  public void execute(QualityExecutionPlan plan) {
    LocalDateTime startedAt = LocalDateTime.now();
    if (!executionRepository.markExecutionRunning(plan.executionId(), startedAt)) return;
    int passed = 0;
    int failed = 0;
    int errors = 0;
    try {
      for (int index = 0; index < plan.rules().size(); index++) {
        if (workflowExecutionRepository.isCanceled(plan.executionId())) return;
        RuleSnapshot rule = plan.rules().get(index);
        RuleOutcome outcome = executeRule(plan, rule);
        switch (outcome.result()) {
          case PASSED -> passed++;
          case NOT_PASSED -> failed++;
          case ERROR -> errors++;
          default -> throw new IllegalStateException("不支持的规则执行结果：" + outcome.result());
        }
        if (workflowExecutionRepository.isCanceled(plan.executionId())) return;
        if (plan.ruleFailureAction() == RuleFailureAction.STOP
            && outcome.result() != CheckResult.PASSED) {
          markRemainingRulesNotRun(plan, index + 1);
          break;
        }
      }
      if (workflowExecutionRepository.isCanceled(plan.executionId())) return;
      LocalDateTime finishedAt = LocalDateTime.now();
      CheckResult finalResult = errors > 0
          ? CheckResult.ERROR
          : failed > 0 ? CheckResult.NOT_PASSED : CheckResult.PASSED;
      executionRepository.completeExecution(
          plan.executionId(), finalResult, passed, failed, errors, finishedAt,
          durationMillis(startedAt, finishedAt));
      monitorRepository.updateMonitorResult(
          plan.monitor().id(), plan.executionNo(), finalResult, finishedAt);
      alertRecorder.recordIfNecessary(plan, finalResult, passed, failed, errors);
    } catch (RuntimeException exception) {
      if (workflowExecutionRepository.isCanceled(plan.executionId())) return;
      LocalDateTime finishedAt = LocalDateTime.now();
      executionRepository.failExecution(
          plan.executionId(), message(exception), finishedAt,
          durationMillis(startedAt, finishedAt));
      monitorRepository.updateMonitorResult(
          plan.monitor().id(), plan.executionNo(), CheckResult.ERROR, finishedAt);
      alertRecorder.recordIfNecessary(plan, CheckResult.ERROR, passed, failed, errors + 1);
    }
  }

  private RuleOutcome executeRule(QualityExecutionPlan plan, RuleSnapshot rule) {
    LocalDateTime startedAt = LocalDateTime.now();
    String sql = null;
    String expected = null;
    try {
      CompiledRule compiled = compiler.compile(plan.monitor(), rule);
      sql = compiled.sql();
      expected = compiled.expectedValue();
      var result = catalogGateway.preview(plan.monitor().dataSourceId(), sql);
      MetricMeasurement measurement = compiler.measure(result);
      boolean passed = evaluator.passes(
          compiled.operator(), compiled.threshold(), compiled.thresholdEnd(), measurement);
      CheckResult checkResult = passed ? CheckResult.PASSED : CheckResult.NOT_PASSED;
      LocalDateTime finishedAt = LocalDateTime.now();
      String metric = measurement.displayValue() + (compiled.unit() == null ? "" : compiled.unit());
      executionRepository.insertRuleExecution(new RuleExecutionSpec(
          plan.executionId(), rule.id(), rule.name(), rule.templateCode(), rule.ruleType(),
          rule.columnName(), checkResult, metric, expected, sql, null,
          durationMillis(startedAt, finishedAt)));
      return new RuleOutcome(checkResult);
    } catch (RuntimeException exception) {
      LocalDateTime finishedAt = LocalDateTime.now();
      executionRepository.insertRuleExecution(new RuleExecutionSpec(
          plan.executionId(), rule.id(), rule.name(), rule.templateCode(), rule.ruleType(),
          rule.columnName(), CheckResult.ERROR, null, expected, sql, message(exception),
          durationMillis(startedAt, finishedAt)));
      return new RuleOutcome(CheckResult.ERROR);
    }
  }

  private void markRemainingRulesNotRun(QualityExecutionPlan plan, int startIndex) {
    for (int index = startIndex; index < plan.rules().size(); index++) {
      RuleSnapshot rule = plan.rules().get(index);
      executionRepository.insertRuleExecution(new RuleExecutionSpec(
          plan.executionId(), rule.id(), rule.name(), rule.templateCode(), rule.ruleType(),
          rule.columnName(), CheckResult.NOT_RUN, null, null, null,
          "前序规则失败，已按监控策略停止执行", 0L));
    }
  }

  private static long durationMillis(LocalDateTime start, LocalDateTime end) {
    return Math.max(0L, Duration.between(start, end).toMillis());
  }

  private static String message(Throwable throwable) {
    String message = throwable.getMessage();
    String normalized = message == null || message.isBlank()
        ? throwable.getClass().getSimpleName()
        : message.trim();
    return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
  }

  private record RuleOutcome(CheckResult result) {}
}
