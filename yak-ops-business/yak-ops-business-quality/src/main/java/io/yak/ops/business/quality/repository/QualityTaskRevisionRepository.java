package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.domain.QualityTaskRevision;
import java.util.Optional;

/** Persistence boundary for immutable workflow-facing quality revisions. */
public interface QualityTaskRevisionRepository {
  Optional<QualityTaskRevision> findLatest(long monitorId);
  Optional<QualityTaskRevision> find(long monitorId, long revisionId);
  QualityTaskRevision insert(
      long monitorId,
      int revisionNo,
      String monitorName,
      String definitionJson,
      String checksum);
}
