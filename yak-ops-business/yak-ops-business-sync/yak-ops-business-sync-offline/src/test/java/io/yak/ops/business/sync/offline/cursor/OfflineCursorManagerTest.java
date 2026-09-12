package io.yak.ops.business.sync.offline.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineSyncCursorRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OfflineCursorManagerTest {

  @Test
  void advancesOnlyFromSucceededCursorRangeBatch() {
    OfflineSyncCursorRepository repository = Mockito.mock(OfflineSyncCursorRepository.class);
    OfflineCursorManager manager = new OfflineCursorManager(repository);
    OfflineSyncCursor current =
        new OfflineSyncCursor(10L, "orders-updated", "updated_at", "100", null, 1L);
    when(repository.find(10L, "orders-updated")).thenReturn(Optional.of(current));
    when(repository.advance(current, "100", "200", 77L)).thenReturn(true);

    assertThat(manager.advanceAfterSucceededBatch(batch(BatchStatus.SUCCEEDED, "100", "200")))
        .isEqualTo(OfflineCursorGateway.AdvanceResult.ADVANCED);
    verify(repository).advance(current, "100", "200", 77L);
  }

  @Test
  void failedBatchNeverAdvancesCursor() {
    OfflineSyncCursorRepository repository = Mockito.mock(OfflineSyncCursorRepository.class);
    OfflineCursorManager manager = new OfflineCursorManager(repository);

    assertThat(manager.advanceAfterSucceededBatch(batch(BatchStatus.FAILED, "100", "200")))
        .isEqualTo(OfflineCursorGateway.AdvanceResult.NOT_SUCCEEDED);
    verify(repository, never()).advance(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void staleSucceededBatchCannotMoveCursorBackwardOrSkipPosition() {
    OfflineSyncCursorRepository repository = Mockito.mock(OfflineSyncCursorRepository.class);
    OfflineCursorManager manager = new OfflineCursorManager(repository);
    OfflineSyncCursor current =
        new OfflineSyncCursor(10L, "orders-updated", "updated_at", "250", 70L, 3L);
    when(repository.find(10L, "orders-updated")).thenReturn(Optional.of(current));

    assertThat(manager.advanceAfterSucceededBatch(batch(BatchStatus.SUCCEEDED, "100", "200")))
        .isEqualTo(OfflineCursorGateway.AdvanceResult.STALE);
    verify(repository, never()).advance(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void successfulBootstrapCreatesCursorOnlyAfterBatchSucceeded() {
    OfflineSyncCursorRepository repository = Mockito.mock(OfflineSyncCursorRepository.class);
    OfflineCursorManager manager = new OfflineCursorManager(repository);
    BatchScope.IncrementalRange scope =
        BatchScope.incrementalBootstrap(
            "timestamp-incremental", "updated_at", "route", "2026-09-12 10:00:00");
    when(repository.find(10L, "timestamp-incremental")).thenReturn(Optional.empty());

    assertThat(manager.advanceAfterSucceededBatch(incrementalBatch(BatchStatus.SUCCEEDED, scope)))
        .isEqualTo(OfflineCursorGateway.AdvanceResult.INITIALIZED);
    verify(repository)
        .commitInitialSuccess(
            10L,
            "timestamp-incremental",
            "updated_at",
            "route",
            "2026-09-12 10:00:00",
            77L);
  }

  @Test
  void failedBootstrapDoesNotCreateCursor() {
    OfflineSyncCursorRepository repository = Mockito.mock(OfflineSyncCursorRepository.class);
    OfflineCursorManager manager = new OfflineCursorManager(repository);
    BatchScope.IncrementalRange scope =
        BatchScope.incrementalBootstrap(
            "timestamp-incremental", "updated_at", "route", "2026-09-12 10:00:00");

    assertThat(manager.advanceAfterSucceededBatch(incrementalBatch(BatchStatus.FAILED, scope)))
        .isEqualTo(OfflineCursorGateway.AdvanceResult.NOT_SUCCEEDED);
    verify(repository, never())
        .commitInitialSuccess(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any(),
            org.mockito.ArgumentMatchers.anyLong());
  }

  private BatchExecution batch(BatchStatus status, String after, String through) {
    return new BatchExecution(
        77L,
        10L,
        BatchKey.backfill(
            "bf-1", BatchScope.cursorRange("orders-updated", after, through).fingerprint()),
        BatchTrigger.BACKFILL,
        BatchScope.cursorRange("orders-updated", after, through),
        new ExecutionSnapshot(
            "{}", 1, new RetryPolicySnapshot(2, 10), "digest", "{\"kind\":\"BatchSyncJob\"}"),
        status,
        List.of());
  }

  private BatchExecution incrementalBatch(
      BatchStatus status, BatchScope.IncrementalRange scope) {
    return new BatchExecution(
        77L,
        10L,
        BatchKey.manual("manual-1"),
        BatchTrigger.MANUAL,
        scope,
        new ExecutionSnapshot(
            "{}", 1, new RetryPolicySnapshot(2, 10), "digest", "{\"kind\":\"BatchSyncJob\"}"),
        status,
        List.of());
  }
}
