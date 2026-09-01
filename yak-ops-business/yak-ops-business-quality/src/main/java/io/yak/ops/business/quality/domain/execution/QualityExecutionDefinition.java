package io.yak.ops.business.quality.domain.execution;

import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.MonitorSnapshot;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.RuleSnapshot;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import java.util.List;

/** Immutable quality definition that can be pinned by an external orchestrator. */
public record QualityExecutionDefinition(
    long projectId,
    MonitorSnapshot monitor,
    List<RuleSnapshot> rules,
    RuleFailureAction ruleFailureAction,
    boolean notifyEnabled,
    NotifyChannel notifyChannel,
    String notifyTarget,
    AlertLevel alertLevel) {

  public QualityExecutionDefinition {
    if (projectId <= 0L) throw new IllegalArgumentException("质量执行定义 Project ID 不合法");
    if (monitor == null) throw new IllegalArgumentException("质量执行定义监控快照不能为空");
    rules = rules == null ? List.of() : List.copyOf(rules);
    ruleFailureAction =
        ruleFailureAction == null ? RuleFailureAction.CONTINUE : ruleFailureAction;
    notifyChannel = notifyChannel == null ? NotifyChannel.MESSAGE : notifyChannel;
    alertLevel = alertLevel == null ? AlertLevel.WARNING : alertLevel;
  }
}
