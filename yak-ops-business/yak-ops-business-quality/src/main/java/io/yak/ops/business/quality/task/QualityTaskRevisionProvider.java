package io.yak.ops.business.quality.task;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityTaskRevision;
import io.yak.ops.business.quality.repository.QualityTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.spi.TaskAssetRevisionProvider;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves immutable Data Quality monitor revisions for Task Catalog consumers. */
@Component
@ConditionalOnQualityEnabled
public class QualityTaskRevisionProvider implements TaskAssetRevisionProvider {
  private final QualityTaskRevisionRepository revisionRepository;

  public QualityTaskRevisionProvider(QualityTaskRevisionRepository revisionRepository) {
    this.revisionRepository = revisionRepository;
  }

  @Override
  public TaskAssetSource source() {
    return TaskAssetSource.DATA_QUALITY;
  }

  @Override
  public Optional<TaskSourceRevision> resolve(String sourceRef, long revisionId) {
    long monitorId = parseMonitorId(sourceRef);
    return revisionRepository.find(monitorId, revisionId).map(this::sourceRevision);
  }

  private TaskSourceRevision sourceRevision(QualityTaskRevision revision) {
    TaskDefinition definition = new TaskDefinition(
        QualityTaskPublisher.TASK_TYPE,
        1,
        revision.monitorName(),
        revision.definitionJson());
    return new TaskSourceRevision(
        revision.id(),
        revision.revisionNo(),
        definition,
        revision.checksum(),
        revision.projectId());
  }

  private long parseMonitorId(String sourceRef) {
    if (sourceRef == null || sourceRef.isBlank()) {
      throw new IllegalArgumentException("Data Quality sourceRef 不能为空");
    }
    try {
      long value = Long.parseLong(sourceRef.trim());
      if (value <= 0L) throw new NumberFormatException("not positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("非法 Data Quality sourceRef：" + sourceRef, exception);
    }
  }
}
