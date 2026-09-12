package io.yak.ops.business.sync.offline.cursor;

import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import java.util.Optional;

/** Cursor 子系统对其他离线同步子系统暴露的稳定边界。 */
public interface OfflineCursorGateway {

  OfflineSyncCursor initializeIfAbsent(
      long taskId, String cursorId, String sourceColumn, String initialPosition);

  Optional<OfflineSyncCursor> find(long taskId, String cursorId);

  String requireSourceColumn(long taskId, String cursorId);

  AdvanceResult advanceAfterSucceededBatch(BatchExecution batch);

  enum AdvanceResult {
    NOT_CURSOR_SCOPE,
    NOT_SUCCEEDED,
    NOT_INITIALIZED,
    INITIALIZED,
    ADVANCED,
    ALREADY_ADVANCED,
    STALE
  }
}
