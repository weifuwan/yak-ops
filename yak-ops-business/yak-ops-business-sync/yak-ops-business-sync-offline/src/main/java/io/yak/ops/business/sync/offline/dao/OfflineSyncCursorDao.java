package io.yak.ops.business.sync.offline.dao;

import io.yak.ops.common.bean.po.sync.offline.OfflineSyncCursorPO;
import java.time.LocalDateTime;

/** 离线同步 Cursor 数据访问接口。 */
public interface OfflineSyncCursorDao {

  OfflineSyncCursorPO select(Long taskId, String cursorId);

  boolean insert(OfflineSyncCursorPO cursorPO);

  boolean advance(
      Long taskId,
      String cursorId,
      String expectedPosition,
      long expectedVersion,
      String nextPosition,
      Long succeededBatchId,
      LocalDateTime updateTime);

  boolean bindSourceSignature(
      Long taskId,
      String cursorId,
      long expectedVersion,
      String sourceSignature,
      LocalDateTime updateTime);
}
