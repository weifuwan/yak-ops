package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import java.util.Optional;

/** OfflineSyncTask Cursor 领域仓储。 */
public interface OfflineSyncCursorRepository {

  Optional<OfflineSyncCursor> find(long taskId, String cursorId);

  OfflineSyncCursor initializeIfAbsent(
      long taskId,
      String cursorId,
      String sourceColumn,
      String initialPosition);

  OfflineSyncCursor commitInitialSuccess(
      long taskId,
      String cursorId,
      String sourceColumn,
      String sourceSignature,
      String initialPosition,
      long succeededBatchId);

  OfflineSyncCursor bindSourceSignature(
      OfflineSyncCursor current, String sourceSignature);

  boolean advance(
      OfflineSyncCursor current,
      String expectedPosition,
      String nextPosition,
      long succeededBatchId);
}
