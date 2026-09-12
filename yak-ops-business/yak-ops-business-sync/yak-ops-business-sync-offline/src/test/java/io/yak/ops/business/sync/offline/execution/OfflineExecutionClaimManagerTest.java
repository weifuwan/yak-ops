package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.incremental.OfflineIncrementalPlanner;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineExecutionClaimManagerTest {

  @Mock private OfflineJobDefinitionService definitionService;
  @Mock private OfflineJobDefinitionRepository definitionRepository;
  @Mock private OfflineJobExecutionRepository executionRepository;
  @Mock private OfflineBatchExecutionRepository batchRepository;
  @Mock private OfflineScheduleRepository scheduleRepository;
  @Mock private OfflineBatchRuntime batchRuntime;
  @Mock private OfflineExecutionAttemptFactory attemptFactory;
  @Mock private OfflineExistingBatchClaimManager existingBatchClaimManager;
  @Mock private OfflineIncrementalPlanner incrementalPlanner;

  private OfflineExecutionClaimManager manager;

  @BeforeEach
  void setUp() {
    manager = new OfflineExecutionClaimManager(
        definitionService,
        definitionRepository,
        executionRepository,
        batchRepository,
        scheduleRepository,
        batchRuntime,
        new OfflineSyncProperties(),
        attemptFactory,
        existingBatchClaimManager,
        incrementalPlanner);
    org.mockito.Mockito.lenient()
        .when(incrementalPlanner.plan(org.mockito.ArgumentMatchers.anyLong(), anyString()))
        .thenReturn(BatchScope.fullSelection());
  }

  @Test
  void initialClaimCreatesBatchThenAttemptThroughFactory() {
    OfflineJobDefinition definition = definition();
    OfflineJobExecution execution = execution();
    when(definitionService.require(10L)).thenReturn(definition);
    when(definitionService.resolveLogicalJobSpec(definition))
        .thenReturn("{\"job\":\"spec\"}");
    stubBatchInsert(77L);
    when(attemptFactory.create(any(BatchExecution.class), eq(1), eq("WORKFLOW"), eq(null), anyString()))
        .thenReturn(execution);
    when(executionRepository.insert(execution))
        .thenAnswer(ignored -> {
          execution.setId(99L);
          return true;
        });

    OfflineExecutionClaim result = manager.claim(10L, "WORKFLOW", null, 1);

    assertThat(result.getExecution().getId()).isEqualTo(99L);
    verify(definitionRepository).lock(10L);
    verify(batchRuntime).hasOccupyingBatch(10L);
    verify(batchRepository).insert(any(BatchExecution.class));
    verify(attemptFactory)
        .create(any(BatchExecution.class), eq(1), eq("WORKFLOW"), eq(null), anyString());
    verify(executionRepository).insert(execution);
    verify(batchRuntime).refreshBatch(77L);
    verifyNoInteractions(existingBatchClaimManager);
  }

  @Test
  void workflowSnapshotKeepsAttemptIdempotencyKey() {
    OfflineJobDefinition definition = definition();
    OfflineJobExecution execution = execution();
    when(definitionService.require(10L)).thenReturn(definition);
    when(executionRepository.findByIdempotencyKey("attempt-123")).thenReturn(Optional.empty());
    stubBatchInsert(78L);
    when(attemptFactory.create(
            any(BatchExecution.class),
            eq(1),
            eq("WORKFLOW"),
            eq(null),
            eq("attempt-123")))
        .thenReturn(execution);
    when(executionRepository.insert(execution))
        .thenAnswer(ignored -> {
          execution.setId(100L);
          return true;
        });

    OfflineExecutionClaim result = manager.claimSnapshot(
        10L,
        3L,
        "digest",
        "{}",
        "{\"job\":\"spec\"}",
        "WORKFLOW",
        "attempt-123");

    assertThat(result.isReused()).isFalse();
    verify(attemptFactory)
        .create(
            any(BatchExecution.class),
            eq(1),
            eq("WORKFLOW"),
            eq(null),
            eq("attempt-123"));
    verify(batchRuntime).refreshBatch(78L);
  }

  @Test
  void workflowIdempotencyReuseReadsFrozenBatchSnapshot() {
    OfflineJobDefinition definition = definition();
    when(definitionService.require(10L)).thenReturn(definition);
    OfflineJobExecution existing = execution();
    existing.setId(101L);
    existing.setJobDefinitionId(10L);
    existing.setBatchId(77L);
    existing.setIdempotencyKey("attempt-123");
    existing.setStatus("SUBMITTED");
    when(executionRepository.findByIdempotencyKey("attempt-123"))
        .thenReturn(Optional.of(existing));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(workflowBatch()));

    OfflineExecutionClaim result = manager.claimSnapshot(
        10L,
        3L,
        "digest",
        "{}",
        "{\"job\":\"spec\"}",
        "WORKFLOW",
        "attempt-123");

    assertThat(result.getExecution()).isSameAs(existing);
    assertThat(result.getLogicalJobSpecJson()).isEqualTo("{\"job\":\"spec\"}");
    assertThat(result.isReused()).isTrue();
    verify(batchRuntime, never()).hasOccupyingBatch(10L);
    verify(attemptFactory, never()).create(any(), any(Integer.class), anyString(), any(), anyString());
  }

  @Test
  void legacyIdempotencyReuseWithoutBatchIdentityIsRejected() {
    OfflineJobDefinition definition = definition();
    when(definitionService.require(10L)).thenReturn(definition);
    OfflineJobExecution history = execution();
    history.setId(101L);
    history.setJobDefinitionId(10L);
    history.setIdempotencyKey("attempt-123");
    history.setStatus("SUCCEEDED");
    when(executionRepository.findByIdempotencyKey("attempt-123"))
        .thenReturn(Optional.of(history));

    assertThatThrownBy(
            () -> manager.claimSnapshot(
                10L,
                3L,
                "digest",
                "{}",
                "{\"job\":\"spec\"}",
                "WORKFLOW",
                "attempt-123"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未绑定 Batch")
        .hasMessageContaining("仅支持查询");
  }

  @Test
  void existingBatchClaimsDelegateToSpecializedManager() {
    OfflineExecutionClaim retry = new OfflineExecutionClaim(null, "retry", execution());
    OfflineExecutionClaim backfill = new OfflineExecutionClaim(null, "backfill", execution());
    when(existingBatchClaimManager.claimRetry(99L)).thenReturn(retry);
    when(existingBatchClaimManager.claimPendingBackfill(77L)).thenReturn(backfill);

    assertThat(manager.claimRetry(99L)).isSameAs(retry);
    assertThat(manager.claimPendingBackfill(77L)).isSameAs(backfill);

    verify(existingBatchClaimManager).claimRetry(99L);
    verify(existingBatchClaimManager).claimPendingBackfill(77L);
  }

  private OfflineJobDefinition definition() {
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    definition.setReleaseState("ONLINE");
    definition.setVersion(3);
    definition.setConfigDigest("digest");
    definition.setDefinitionJson("{}");
    return definition;
  }

  private OfflineJobExecution execution() {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setStatus("CREATED");
    return execution;
  }

  private BatchExecution workflowBatch() {
    return new BatchExecution(
        77L,
        10L,
        BatchKey.workflow("attempt-123"),
        BatchTrigger.WORKFLOW,
        BatchScope.fullSelection(),
        new ExecutionSnapshot(
            "{}",
            3,
            new RetryPolicySnapshot(1, 0),
            "digest",
            "{\"job\":\"spec\"}"),
        BatchStatus.RUNNING,
        List.of());
  }

  private void stubBatchInsert(long id) {
    AtomicReference<BatchExecution> saved = new AtomicReference<>();
    when(batchRepository.insert(any(BatchExecution.class)))
        .thenAnswer(invocation -> {
          BatchExecution batch = invocation.getArgument(0);
          BatchExecution inserted = new BatchExecution(
              id,
              batch.taskId(),
              batch.batchKey(),
              batch.trigger(),
              batch.batchScope(),
              batch.snapshot(),
              batch.status(),
              batch.attempts());
          saved.set(inserted);
          return inserted;
        });
    when(batchRepository.findById(id)).thenAnswer(ignored -> Optional.ofNullable(saved.get()));
  }
}
