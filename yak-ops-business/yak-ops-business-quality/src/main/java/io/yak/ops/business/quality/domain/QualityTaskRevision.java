package io.yak.ops.business.quality.domain;

import java.time.LocalDateTime;

/** Immutable workflow-facing revision owned by the Data Quality domain. */
public record QualityTaskRevision(
    long id,
    long projectId,
    long monitorId,
    int revisionNo,
    String monitorName,
    String definitionJson,
    String checksum,
    LocalDateTime createdAt) {

  public QualityTaskRevision {
    if (id <= 0L) throw new IllegalArgumentException("质量任务 revision ID 不合法");
    if (projectId <= 0L) throw new IllegalArgumentException("质量任务 Project ID 不合法");
    if (monitorId <= 0L) throw new IllegalArgumentException("质量监控 ID 不合法");
    if (revisionNo <= 0) throw new IllegalArgumentException("质量任务 revisionNo 不合法");
    if (monitorName == null || monitorName.isBlank()) {
      throw new IllegalArgumentException("质量任务名称不能为空");
    }
    if (definitionJson == null || definitionJson.isBlank()) {
      throw new IllegalArgumentException("质量任务执行定义不能为空");
    }
    if (checksum == null || checksum.isBlank()) {
      throw new IllegalArgumentException("质量任务 checksum 不能为空");
    }
  }
}
