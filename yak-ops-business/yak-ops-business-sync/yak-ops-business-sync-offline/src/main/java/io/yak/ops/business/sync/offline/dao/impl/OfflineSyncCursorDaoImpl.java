package io.yak.ops.business.sync.offline.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineSyncCursorDao;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineSyncCursorMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineSyncCursorPO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的离线同步 Cursor 数据访问实现。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineSyncCursorDaoImpl implements OfflineSyncCursorDao {

  private final OfflineSyncCursorMapper mapper;

  @Override
  public OfflineSyncCursorPO select(Long taskId, String cursorId) {
    if (taskId == null || taskId <= 0L || !StringUtils.hasText(cursorId)) return null;
    return mapper.selectOne(
        Wrappers.<OfflineSyncCursorPO>lambdaQuery()
            .eq(OfflineSyncCursorPO::getJobDefinitionId, taskId)
            .eq(OfflineSyncCursorPO::getCursorId, cursorId.trim())
            .last("LIMIT 1"));
  }

  @Override
  public boolean insert(OfflineSyncCursorPO cursorPO) {
    return mapper.insert(cursorPO) > 0;
  }

  @Override
  public boolean advance(
      Long taskId,
      String cursorId,
      String expectedPosition,
      long expectedVersion,
      String nextPosition,
      Long succeededBatchId,
      LocalDateTime updateTime) {
    if (taskId == null
        || taskId <= 0L
        || !StringUtils.hasText(cursorId)
        || !StringUtils.hasText(expectedPosition)
        || expectedVersion < 1L
        || !StringUtils.hasText(nextPosition)
        || succeededBatchId == null
        || succeededBatchId <= 0L) {
      return false;
    }
    return mapper.update(
        null,
        Wrappers.<OfflineSyncCursorPO>lambdaUpdate()
            .eq(OfflineSyncCursorPO::getJobDefinitionId, taskId)
            .eq(OfflineSyncCursorPO::getCursorId, cursorId.trim())
            .eq(OfflineSyncCursorPO::getPositionValue, expectedPosition.trim())
            .eq(OfflineSyncCursorPO::getStateVersion, expectedVersion)
            .set(OfflineSyncCursorPO::getPositionValue, nextPosition.trim())
            .set(OfflineSyncCursorPO::getLastSucceededBatchId, succeededBatchId)
            .set(OfflineSyncCursorPO::getStateVersion, expectedVersion + 1L)
            .set(OfflineSyncCursorPO::getUpdateTime, updateTime)) > 0;
  }

  @Override
  public boolean bindSourceSignature(
      Long taskId,
      String cursorId,
      long expectedVersion,
      String sourceSignature,
      LocalDateTime updateTime) {
    if (taskId == null
        || taskId <= 0L
        || !StringUtils.hasText(cursorId)
        || expectedVersion < 1L
        || !StringUtils.hasText(sourceSignature)) {
      return false;
    }
    return mapper.update(
            null,
            Wrappers.<OfflineSyncCursorPO>lambdaUpdate()
                .eq(OfflineSyncCursorPO::getJobDefinitionId, taskId)
                .eq(OfflineSyncCursorPO::getCursorId, cursorId.trim())
                .eq(OfflineSyncCursorPO::getStateVersion, expectedVersion)
                .isNull(OfflineSyncCursorPO::getSourceSignature)
                .set(OfflineSyncCursorPO::getSourceSignature, sourceSignature.trim())
                .set(OfflineSyncCursorPO::getStateVersion, expectedVersion + 1L)
                .set(OfflineSyncCursorPO::getUpdateTime, updateTime))
        > 0;
  }
}
