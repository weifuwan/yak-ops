package io.yak.ops.business.sync.offline.cursor;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.repository.OfflineSyncCursorRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Internal Cursor owner; other subsystems enter only through OfflineCursorGateway. */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineCursorManager implements OfflineCursorGateway {

  private final OfflineSyncCursorRepository repository;

  @Override
  public OfflineSyncCursor initializeIfAbsent(
      long taskId, String cursorId, String sourceColumn, String initialPosition) {
    return repository.initializeIfAbsent(taskId, cursorId, sourceColumn, initialPosition);
  }

  @Override
  public Optional<OfflineSyncCursor> find(long taskId, String cursorId) {
    return repository.find(taskId, cursorId);
  }

  @Override
  public String requireSourceColumn(long taskId, String cursorId) {
    return find(taskId, cursorId)
        .orElseThrow(() -> new IllegalStateException("Cursor 尚未初始化：" + cursorId))
        .sourceColumn();
  }

  @Override
  public AdvanceResult advanceAfterSucceededBatch(BatchExecution batch) {
    Objects.requireNonNull(batch, "BatchExecution 不能为空");
    if (batch.batchScope() instanceof BatchScope.IncrementalRange range) {
      return commitIncremental(batch, range);
    }
    if (!(batch.batchScope() instanceof BatchScope.CursorRange range)) {
      return AdvanceResult.NOT_CURSOR_SCOPE;
    }
    if (batch.status() != BatchStatus.SUCCEEDED) {
      return AdvanceResult.NOT_SUCCEEDED;
    }
    if (batch.id() == null || batch.id() <= 0L) {
      throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    }

    OfflineSyncCursor current = repository.find(batch.taskId(), range.cursorId()).orElse(null);
    Optional<AdvanceResult> currentState = currentState(current, range);
    if (currentState.isPresent()) {
      return currentState.get();
    }

    if (repository.advance(current, range.afterExclusive(), range.throughInclusive(), batch.id())) {
      return AdvanceResult.ADVANCED;
    }

    OfflineSyncCursor reread = repository.find(batch.taskId(), range.cursorId()).orElse(null);
    if (reread != null && reread.position().equals(range.throughInclusive())) {
      return AdvanceResult.ALREADY_ADVANCED;
    }
    return AdvanceResult.STALE;
  }

  private AdvanceResult commitIncremental(
      BatchExecution batch, BatchScope.IncrementalRange range) {
    if (batch.status() != BatchStatus.SUCCEEDED) {
      return AdvanceResult.NOT_SUCCEEDED;
    }
    if (batch.id() == null || batch.id() <= 0L) {
      throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    }

    OfflineSyncCursor current = repository.find(batch.taskId(), range.cursorId()).orElse(null);
    if (current == null) {
      if (!range.bootstrapFull()) {
        return AdvanceResult.NOT_INITIALIZED;
      }
      repository.commitInitialSuccess(
          batch.taskId(),
          range.cursorId(),
          range.sourceColumn(),
          range.sourceSignature(),
          range.throughInclusive(),
          batch.id());
      return AdvanceResult.INITIALIZED;
    }

    current = validateRoute(current, range);
    if (Objects.equals(current.lastSucceededBatchId(), batch.id())
        || current.position().equals(range.throughInclusive())) {
      return AdvanceResult.ALREADY_ADVANCED;
    }
    if (range.bootstrapFull() || !current.position().equals(range.afterExclusive())) {
      return AdvanceResult.STALE;
    }
    if (repository.advance(
        current, range.afterExclusive(), range.throughInclusive(), batch.id())) {
      return AdvanceResult.ADVANCED;
    }

    OfflineSyncCursor reread = repository.find(batch.taskId(), range.cursorId()).orElse(null);
    if (reread != null
        && (Objects.equals(reread.lastSucceededBatchId(), batch.id())
            || reread.position().equals(range.throughInclusive()))) {
      return AdvanceResult.ALREADY_ADVANCED;
    }
    return AdvanceResult.STALE;
  }

  private OfflineSyncCursor validateRoute(
      OfflineSyncCursor current, BatchScope.IncrementalRange range) {
    if (!current.sourceColumn().equals(range.sourceColumn())) {
      throw new IllegalStateException("Cursor 已绑定不同增量字段：" + range.cursorId());
    }
    if (current.sourceSignature() == null) {
      return repository.bindSourceSignature(current, range.sourceSignature());
    }
    if (!current.sourceSignature().equals(range.sourceSignature())) {
      throw new IllegalStateException("Cursor 已绑定不同来源路由：" + range.cursorId());
    }
    return current;
  }

  private Optional<AdvanceResult> currentState(
      OfflineSyncCursor current, BatchScope.CursorRange range) {
    if (current == null) {
      return Optional.of(AdvanceResult.NOT_INITIALIZED);
    }
    if (current.position().equals(range.throughInclusive())) {
      return Optional.of(AdvanceResult.ALREADY_ADVANCED);
    }
    if (!current.position().equals(range.afterExclusive())) {
      return Optional.of(AdvanceResult.STALE);
    }
    return Optional.empty();
  }
}
