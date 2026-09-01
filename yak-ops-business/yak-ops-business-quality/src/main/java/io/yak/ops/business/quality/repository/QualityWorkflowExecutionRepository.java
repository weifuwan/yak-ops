package io.yak.ops.business.quality.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.mapper.QualityExecutionMapper;
import io.yak.ops.business.quality.domain.execution.QualityExecutionDefinition;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.MonitorSnapshot;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** Persistence adapter for Workflow-specific quality execution admission and cancellation. */
@Repository
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityWorkflowExecutionRepository {
  private final QualityExecutionMapper executionMapper;
  private final CurrentProject currentProject;

  public QualityWorkflowExecutionRepository(
      QualityExecutionMapper executionMapper,
      CurrentProject currentProject) {
    this.executionMapper = executionMapper;
    this.currentProject = currentProject;
  }

  public Optional<String> findExecutionNoByIdempotencyKey(String idempotencyKey) {
    String normalized = normalizeIdempotencyKey(idempotencyKey);
    if (normalized == null) return Optional.empty();
    QualityExecutionPO po = executionMapper.selectOne(
        Wrappers.<QualityExecutionPO>lambdaQuery()
            .eq(QualityExecutionPO::getProjectId, currentProject.requireProjectId())
            .eq(QualityExecutionPO::getIdempotencyKey, normalized));
    return Optional.ofNullable(po).map(QualityExecutionPO::getExecutionNo);
  }

  public long insert(
      String executionNo,
      QualityExecutionDefinition definition,
      String operator,
      TriggerType triggerType,
      LocalDateTime queuedAt,
      String idempotencyKey) {
    requireProject(definition.projectId());
    MonitorSnapshot monitor = definition.monitor();
    QualityExecutionPO po = new QualityExecutionPO();
    po.setProjectId(definition.projectId());
    po.setExecutionNo(executionNo);
    po.setIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
    po.setMonitorId(monitor.id());
    po.setMonitorName(monitor.name());
    po.setDataSourceId(monitor.dataSourceId());
    po.setDataSourceName(monitor.dataSourceName());
    po.setDatabaseName(monitor.databaseName());
    po.setSchemaName(monitor.schemaName());
    po.setTableName(monitor.tableName());
    po.setObjectName(objectName(monitor));
    po.setTriggerType(triggerType.name());
    po.setExecutionStatus("WAITING");
    po.setCheckResult("RUNNING");
    po.setTotalRules(definition.rules().size());
    po.setPassedRules(0);
    po.setFailedRules(0);
    po.setErrorRules(0);
    po.setOperatorName(operator);
    po.setQueuedAt(queuedAt);
    executionMapper.insert(po);
    if (po.getId() == null) {
      throw new IllegalStateException("Workflow 质量检查已创建，但未返回执行 ID");
    }
    return po.getId();
  }

  public boolean cancel(String executionNo, LocalDateTime finishedAt) {
    return executionMapper.update(
            null,
            Wrappers.<QualityExecutionPO>lambdaUpdate()
                .eq(QualityExecutionPO::getProjectId, currentProject.requireProjectId())
                .eq(QualityExecutionPO::getExecutionNo, executionNo)
                .in(QualityExecutionPO::getExecutionStatus, List.of("WAITING", "RUNNING"))
                .set(QualityExecutionPO::getExecutionStatus, "CANCELED")
                .set(QualityExecutionPO::getCheckResult, "NOT_RUN")
                .set(QualityExecutionPO::getFinishedAt, finishedAt)
                .set(QualityExecutionPO::getErrorMessage, "执行已取消"))
        > 0;
  }

  public boolean isCanceled(long executionId) {
    return executionMapper.selectCount(
            Wrappers.<QualityExecutionPO>lambdaQuery()
                .eq(QualityExecutionPO::getProjectId, currentProject.requireProjectId())
                .eq(QualityExecutionPO::getId, executionId)
                .eq(QualityExecutionPO::getExecutionStatus, "CANCELED"))
        > 0;
  }

  private String normalizeIdempotencyKey(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > 255) {
      throw new IllegalArgumentException("质量任务幂等键不能超过 255 个字符");
    }
    return normalized;
  }

  private String objectName(MonitorSnapshot monitor) {
    List<String> parts = new ArrayList<>();
    addPart(parts, monitor.databaseName());
    addPart(parts, monitor.schemaName());
    addPart(parts, monitor.tableName());
    return String.join(".", parts);
  }

  private void addPart(List<String> parts, String value) {
    if (value != null && !value.isBlank()) parts.add(value.trim());
  }

  private void requireProject(long projectId) {
    if (currentProject.requireProjectId() != projectId) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }
}
