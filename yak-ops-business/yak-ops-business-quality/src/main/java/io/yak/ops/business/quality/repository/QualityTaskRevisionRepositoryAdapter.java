package io.yak.ops.business.quality.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.mapper.QualityTaskRevisionMapper;
import io.yak.ops.business.quality.domain.QualityTaskRevision;
import io.yak.ops.common.bean.po.quality.QualityTaskRevisionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for immutable Data Quality workflow revisions. */
@Repository
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityTaskRevisionRepositoryAdapter implements QualityTaskRevisionRepository {
  private final QualityTaskRevisionMapper mapper;
  private final CurrentProject currentProject;

  public QualityTaskRevisionRepositoryAdapter(
      QualityTaskRevisionMapper mapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public Optional<QualityTaskRevision> findLatest(long monitorId) {
    long projectId = currentProject.requireProjectId();
    QualityTaskRevisionPO po = mapper.selectOne(
        Wrappers.<QualityTaskRevisionPO>lambdaQuery()
            .eq(QualityTaskRevisionPO::getProjectId, projectId)
            .eq(QualityTaskRevisionPO::getMonitorId, monitorId)
            .orderByDesc(QualityTaskRevisionPO::getRevisionNo)
            .last("LIMIT 1"));
    return Optional.ofNullable(po).map(this::revision);
  }

  @Override
  public Optional<QualityTaskRevision> find(long monitorId, long revisionId) {
    long projectId = currentProject.requireProjectId();
    QualityTaskRevisionPO po = mapper.selectOne(
        Wrappers.<QualityTaskRevisionPO>lambdaQuery()
            .eq(QualityTaskRevisionPO::getProjectId, projectId)
            .eq(QualityTaskRevisionPO::getMonitorId, monitorId)
            .eq(QualityTaskRevisionPO::getId, revisionId));
    return Optional.ofNullable(po).map(this::revision);
  }

  @Override
  public QualityTaskRevision insert(
      long monitorId,
      int revisionNo,
      String monitorName,
      String definitionJson,
      String checksum) {
    QualityTaskRevisionPO po = new QualityTaskRevisionPO();
    po.setProjectId(currentProject.requireProjectId());
    po.setMonitorId(monitorId);
    po.setRevisionNo(revisionNo);
    po.setMonitorName(monitorName);
    po.setDefinitionJson(definitionJson);
    po.setChecksum(checksum);
    po.setCreatedAt(LocalDateTime.now());
    mapper.insert(po);
    if (po.getId() == null) {
      throw new IllegalStateException("质量任务 revision 已创建，但未返回 ID");
    }
    return revision(po);
  }

  private QualityTaskRevision revision(QualityTaskRevisionPO po) {
    return new QualityTaskRevision(
        po.getId(),
        po.getProjectId(),
        po.getMonitorId(),
        po.getRevisionNo(),
        po.getMonitorName(),
        po.getDefinitionJson(),
        po.getChecksum(),
        po.getCreatedAt());
  }
}
