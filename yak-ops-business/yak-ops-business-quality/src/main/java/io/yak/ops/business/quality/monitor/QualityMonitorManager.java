package io.yak.ops.business.quality.monitor;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettingsSpec;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSpec;
import io.yak.ops.business.quality.domain.QualityDomain.RuleSpec;
import io.yak.ops.business.quality.repository.QualityExecutionRepository;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.business.quality.schedule.QualityScheduleLifecycle;
import io.yak.ops.business.quality.task.QualityTaskPublisher;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transactional lifecycle owner for quality monitor definitions. */
@Component
@ConditionalOnQualityEnabled
public class QualityMonitorManager {
  private final QualityMonitorRepository monitorRepository;
  private final QualityExecutionRepository executionRepository;
  private final QualityMonitorPolicy monitorPolicy;
  private final QualityRulePolicy rulePolicy;
  private final QualityMonitorSettingsPolicy settingsPolicy;
  private final QualityScheduleLifecycle scheduleLifecycle;
  private final QualityTaskPublisher taskPublisher;

  public QualityMonitorManager(
      QualityMonitorRepository monitorRepository,
      QualityExecutionRepository executionRepository,
      QualityMonitorPolicy monitorPolicy,
      QualityRulePolicy rulePolicy,
      QualityMonitorSettingsPolicy settingsPolicy,
      QualityScheduleLifecycle scheduleLifecycle,
      QualityTaskPublisher taskPublisher) {
    this.monitorRepository = monitorRepository;
    this.executionRepository = executionRepository;
    this.monitorPolicy = monitorPolicy;
    this.rulePolicy = rulePolicy;
    this.settingsPolicy = settingsPolicy;
    this.scheduleLifecycle = scheduleLifecycle;
    this.taskPublisher = taskPublisher;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public Monitor create(QualityMonitorCommand.Save command) {
    monitorPolicy.validateTarget(null, command);
    List<RuleSpec> rules = rulePolicy.normalize(command.rules());
    MonitorSettingsSpec settings = settingsPolicy.normalize(command.settings(), null);
    long id = monitorRepository.insertMonitor(toMonitorSpec(command));
    monitorRepository.upsertMonitorSettings(id, settings);
    monitorRepository.replaceRules(id, rules);
    scheduleLifecycle.sync(id);
    Monitor saved = require(id);
    taskPublisher.sync(saved);
    return saved;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public Monitor update(long id, QualityMonitorCommand.Save command) {
    Monitor existing = require(id);
    monitorRepository.lockMonitor(id);
    monitorPolicy.validateTarget(id, command);
    List<RuleSpec> rules = rulePolicy.normalize(command.rules());
    MonitorSettings currentSettings = monitorRepository.findMonitorSettings(existing.id());
    MonitorSettingsSpec settings = settingsPolicy.normalize(command.settings(), currentSettings);
    if (!monitorRepository.updateMonitor(id, toMonitorSpec(command))) {
      throw new IllegalArgumentException("质量监控不存在：" + id);
    }
    monitorRepository.upsertMonitorSettings(id, settings);
    monitorRepository.replaceRules(id, rules);
    scheduleLifecycle.sync(id);
    Monitor saved = require(id);
    taskPublisher.sync(saved);
    return saved;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public boolean delete(long id) {
    require(id);
    if (executionRepository.hasActiveExecution(id)) {
      throw new IllegalStateException("质量监控正在运行，暂时不能删除");
    }
    if (!monitorRepository.deleteMonitor(id)) {
      throw new IllegalArgumentException("质量监控不存在：" + id);
    }
    scheduleLifecycle.remove(id);
    taskPublisher.offline(id);
    return true;
  }

  private Monitor require(long id) {
    return monitorRepository.findMonitor(id)
        .orElseThrow(() -> new IllegalArgumentException("质量监控不存在：" + id));
  }

  private MonitorSpec toMonitorSpec(QualityMonitorCommand.Save command) {
    return new MonitorSpec(
        QualityMonitorPolicy.requireText(command.name(), "质量监控名称不能为空"),
        QualityMonitorPolicy.trimToNull(command.description()),
        command.dataSourceId(),
        QualityMonitorPolicy.requireText(command.dataSourceName(), "数据源名称不能为空"),
        QualityMonitorPolicy.trimToNull(command.databaseName()),
        QualityMonitorPolicy.trimToNull(command.schemaName()),
        QualityMonitorPolicy.requireText(command.tableName(), "数据表名称不能为空"),
        QualityMonitorPolicy.trimToNull(command.whereClause()),
        QualityMonitorPolicy.requireText(command.owner(), "负责人不能为空"),
        command.enabled() == null || command.enabled());
  }
}
