package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineSyncCursorDao;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.common.bean.po.sync.offline.OfflineSyncCursorPO;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** OfflineSyncCursor 与持久化模型之间的适配器；Project 从所属 Definition 继承。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineSyncCursorRepositoryAdapter implements OfflineSyncCursorRepository {

  private final OfflineSyncCursorDao dao;
  private final OfflineJobDefinitionRepository definitionRepository;

  @Override
  public Optional<OfflineSyncCursor> find(long taskId, String cursorId) {
    requireTask(taskId);
    return Optional.ofNullable(toDomain(dao.select(taskId, requireText(cursorId, "cursorId 不能为空"))));
  }

  @Override
  public OfflineSyncCursor initializeIfAbsent(
      long taskId,
      String cursorId,
      String sourceColumn,
      String initialPosition) {
    requireTask(taskId);
    String normalizedId = requireText(cursorId, "cursorId 不能为空");
    String normalizedColumn = requireText(sourceColumn, "sourceColumn 不能为空");
    String normalizedPosition = requireText(initialPosition, "initialPosition 不能为空");

    OfflineSyncCursor existing = toDomain(dao.select(taskId, normalizedId));
    if (existing != null) return validateRoute(existing, normalizedColumn);

    LocalDateTime now = LocalDateTime.now();
    OfflineSyncCursorPO po = new OfflineSyncCursorPO();
    po.setJobDefinitionId(taskId);
    po.setCursorId(normalizedId);
    po.setSourceColumn(normalizedColumn);
    po.setPositionValue(normalizedPosition);
    po.setStateVersion(1L);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    try {
      if (dao.insert(po)) {
        return new OfflineSyncCursor(taskId, normalizedId, normalizedColumn, normalizedPosition, null, 1L);
      }
    } catch (DuplicateKeyException ignored) {
      // 多节点同时初始化时，唯一键决定赢家；下面统一重读并验证 route。
    }
    OfflineSyncCursor concurrent = toDomain(dao.select(taskId, normalizedId));
    if (concurrent == null) {
      throw new IllegalStateException("初始化离线同步 Cursor 失败：" + normalizedId);
    }
    return validateRoute(concurrent, normalizedColumn);
  }

  @Override
  public OfflineSyncCursor commitInitialSuccess(
      long taskId,
      String cursorId,
      String sourceColumn,
      String sourceSignature,
      String initialPosition,
      long succeededBatchId) {
    requireTask(taskId);
    if (succeededBatchId <= 0L) {
      throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    }
    String normalizedId = requireText(cursorId, "cursorId 不能为空");
    String normalizedColumn = requireText(sourceColumn, "sourceColumn 不能为空");
    String normalizedSignature = requireText(sourceSignature, "sourceSignature 不能为空");
    String normalizedPosition = requireText(initialPosition, "initialPosition 不能为空");
    OfflineSyncCursor existing = toDomain(dao.select(taskId, normalizedId));
    if (existing != null) {
      return validateInitialCommit(
          bindAndValidate(existing, normalizedColumn, normalizedSignature),
          normalizedPosition,
          succeededBatchId);
    }

    LocalDateTime now = LocalDateTime.now();
    OfflineSyncCursorPO po = new OfflineSyncCursorPO();
    po.setJobDefinitionId(taskId);
    po.setCursorId(normalizedId);
    po.setSourceColumn(normalizedColumn);
    po.setSourceSignature(normalizedSignature);
    po.setPositionValue(normalizedPosition);
    po.setLastSucceededBatchId(succeededBatchId);
    po.setStateVersion(1L);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    try {
      if (dao.insert(po)) {
        return new OfflineSyncCursor(
            taskId,
            normalizedId,
            normalizedColumn,
            normalizedSignature,
            normalizedPosition,
            succeededBatchId,
            1L);
      }
    } catch (DuplicateKeyException ignored) {
      // Concurrent successful bootstrap: the unique key chooses the cursor owner.
    }
    OfflineSyncCursor concurrent = toDomain(dao.select(taskId, normalizedId));
    if (concurrent == null) {
      throw new IllegalStateException("提交离线同步初始 Cursor 失败：" + normalizedId);
    }
    return validateInitialCommit(
        bindAndValidate(concurrent, normalizedColumn, normalizedSignature),
        normalizedPosition,
        succeededBatchId);
  }

  @Override
  public OfflineSyncCursor bindSourceSignature(
      OfflineSyncCursor current, String sourceSignature) {
    Objects.requireNonNull(current, "current cursor 不能为空");
    String signature = requireText(sourceSignature, "sourceSignature 不能为空");
    if (current.sourceSignature() != null) {
      return validateSignature(current, signature);
    }
    dao.bindSourceSignature(
        current.taskId(),
        current.cursorId(),
        current.stateVersion(),
        signature,
        LocalDateTime.now());
    OfflineSyncCursor rebound = toDomain(dao.select(current.taskId(), current.cursorId()));
    if (rebound == null) throw new IllegalStateException("绑定 Cursor 来源摘要后无法重读");
    return validateSignature(rebound, signature);
  }

  @Override
  public boolean advance(
      OfflineSyncCursor current,
      String expectedPosition,
      String nextPosition,
      long succeededBatchId) {
    Objects.requireNonNull(current, "current cursor 不能为空");
    requireTask(current.taskId());
    String expected = requireText(expectedPosition, "expectedPosition 不能为空");
    String next = requireText(nextPosition, "nextPosition 不能为空");
    if (!current.position().equals(expected)) return false;
    if (succeededBatchId <= 0L) throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    return dao.advance(
        current.taskId(),
        current.cursorId(),
        expected,
        current.stateVersion(),
        next,
        succeededBatchId,
        LocalDateTime.now());
  }

  private void requireTask(long taskId) {
    if (taskId <= 0L) throw new IllegalArgumentException("TaskId 必须大于 0");
    definitionRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务不存在：" + taskId));
  }

  private OfflineSyncCursor validateRoute(OfflineSyncCursor cursor, String sourceColumn) {
    if (!cursor.sourceColumn().equals(sourceColumn)) {
      throw new IllegalStateException(
          "Cursor 已绑定不同 sourceColumn：" + cursor.cursorId());
    }
    return cursor;
  }

  private OfflineSyncCursor bindAndValidate(
      OfflineSyncCursor cursor, String sourceColumn, String sourceSignature) {
    validateRoute(cursor, sourceColumn);
    return bindSourceSignature(cursor, sourceSignature);
  }

  private OfflineSyncCursor validateSignature(
      OfflineSyncCursor cursor, String sourceSignature) {
    if (!sourceSignature.equals(cursor.sourceSignature())) {
      throw new IllegalStateException("Cursor 已绑定不同来源路由：" + cursor.cursorId());
    }
    return cursor;
  }

  private OfflineSyncCursor validateInitialCommit(
      OfflineSyncCursor cursor, String position, long succeededBatchId) {
    if (cursor.position().equals(position)
        || Objects.equals(cursor.lastSucceededBatchId(), succeededBatchId)) {
      return cursor;
    }
    throw new IllegalStateException("Cursor 已由其他成功 Batch 建立，当前初始上界已过期：" + cursor.cursorId());
  }

  private OfflineSyncCursor toDomain(OfflineSyncCursorPO po) {
    if (po == null) return null;
    return new OfflineSyncCursor(
        positive(po.getJobDefinitionId(), "TaskId"),
        requireText(po.getCursorId(), "cursorId 不能为空"),
        requireText(po.getSourceColumn(), "sourceColumn 不能为空"),
        po.getSourceSignature(),
        requireText(po.getPositionValue(), "positionValue 不能为空"),
        po.getLastSucceededBatchId(),
        positive(po.getStateVersion(), "stateVersion"));
  }

  private long positive(Long value, String field) {
    if (value == null || value <= 0L) throw new IllegalStateException(field + " 必须大于 0");
    return value;
  }

  private String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
