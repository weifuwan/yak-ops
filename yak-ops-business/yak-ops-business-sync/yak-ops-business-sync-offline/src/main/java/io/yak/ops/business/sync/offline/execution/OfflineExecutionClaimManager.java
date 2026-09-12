package io.yak.ops.business.sync.offline.execution;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.BatchTriggerToken;
import io.yak.ops.business.sync.offline.domain.core.BatchTriggerToken.Parsed;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.incremental.OfflineIncrementalPlanner;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Owns admission of new Manual / Schedule / Workflow Batch plus Attempt 1. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineExecutionClaimManager {

  private final OfflineJobDefinitionService definitionService;
  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineBatchRuntime batchRuntime;
  private final OfflineSyncProperties properties;
  private final OfflineExecutionAttemptFactory attemptFactory;
  private final OfflineExistingBatchClaimManager existingBatchClaimManager;
  private final OfflineIncrementalPlanner incrementalPlanner;

  public OfflineExecutionClaimManager(
      OfflineJobDefinitionService definitionService,
      OfflineJobDefinitionRepository definitionRepository,
      OfflineJobExecutionRepository executionRepository,
      OfflineBatchExecutionRepository batchRepository,
      OfflineScheduleRepository scheduleRepository,
      OfflineBatchRuntime batchRuntime,
      OfflineSyncProperties properties,
      OfflineExecutionAttemptFactory attemptFactory,
      OfflineExistingBatchClaimManager existingBatchClaimManager,
      OfflineIncrementalPlanner incrementalPlanner) {
    this.definitionService = definitionService;
    this.definitionRepository = definitionRepository;
    this.executionRepository = executionRepository;
    this.batchRepository = batchRepository;
    this.scheduleRepository = scheduleRepository;
    this.batchRuntime = batchRuntime;
    this.properties = properties;
    this.attemptFactory = attemptFactory;
    this.existingBatchClaimManager = existingBatchClaimManager;
    this.incrementalPlanner = incrementalPlanner;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineExecutionClaim claim(
      Long definitionId,
      String triggerType,
      Long retryFromExecutionId,
      int attemptNo) {
    Parsed trigger = BatchTriggerToken.parse(triggerType);
    if (retryFromExecutionId != null || attemptNo > 1 || trigger.retry()) {
      throw new IllegalArgumentException("Retry 必须通过 Batch 内 claimRetry 创建新 Attempt");
    }

    definitionRepository.lock(definitionId);
    OfflineJobDefinition definition = definitionService.require(definitionId);
    if (!"ONLINE".equalsIgnoreCase(definition.getReleaseState())) {
      throw new IllegalStateException("请先上线任务，再执行运行操作");
    }

    return createInitialClaim(
        definition,
        Math.max(1, definition.getVersion() == null ? 1 : definition.getVersion()),
        definition.getConfigDigest(),
        definition.getDefinitionJson(),
        definitionService.resolveLogicalJobSpec(definition),
        trigger,
        null);
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineExecutionClaim claimSnapshot(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String triggerType) {
    return claimSnapshot(
        definitionId,
        definitionVersion,
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        triggerType,
        null);
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineExecutionClaim claimSnapshot(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String triggerType,
      String idempotencyKey) {
    if (!StringUtils.hasText(logicalJobSpecJson)) {
      throw new IllegalArgumentException("任务版本快照缺少 JobSpec");
    }

    Parsed trigger = BatchTriggerToken.parse(triggerType);
    if (trigger.retry()) {
      throw new IllegalArgumentException("Retry 不能通过 claimSnapshot 创建新 Batch");
    }

    definitionRepository.lock(definitionId);
    OfflineJobDefinition current = definitionService.require(definitionId);
    String normalizedKey = normalizeOptionalIdempotencyKey(idempotencyKey);
    if (normalizedKey != null) {
      OfflineJobExecution existing =
          executionRepository.findByIdempotencyKey(normalizedKey).orElse(null);
      if (existing != null) {
        BatchExecution existingBatch =
            validateIdempotentReuse(
                existing,
                definitionId,
                definitionVersion,
                configDigest,
                definitionSnapshotJson,
                logicalJobSpecJson);
        return new OfflineExecutionClaim(
            current,
            existingBatch.snapshot().logicalJobSpec(),
            existing,
            true);
      }
    }

    return createInitialClaim(
        current,
        Math.max(1L, definitionVersion),
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        trigger,
        normalizedKey);
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineExecutionClaim claimRetry(Long retryFromExecutionId) {
    return existingBatchClaimManager.claimRetry(retryFromExecutionId);
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineExecutionClaim claimPendingBackfill(Long batchId) {
    return existingBatchClaimManager.claimPendingBackfill(batchId);
  }

  private OfflineExecutionClaim createInitialClaim(
      OfflineJobDefinition definition,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      Parsed trigger,
      String idempotencyKey) {
    Long definitionId = definition.getId();
    if (batchRuntime.hasOccupyingBatch(definitionId)) {
      throw new IllegalStateException("任务已有运行中的 BatchExecution，不能重复提交");
    }

    String effectiveIdempotencyKey =
        idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey;
    Long batchId =
        createBatch(
            definitionId,
            definitionVersion,
            configDigest,
            definitionSnapshotJson,
            logicalJobSpecJson,
            trigger,
            effectiveIdempotencyKey);
    BatchExecution batch =
        batchRepository
            .findById(batchId)
            .orElseThrow(
                () -> new IllegalStateException("创建后无法读取 BatchExecution：" + batchId));

    OfflineJobExecution execution =
        attemptFactory.create(
            batch,
            1,
            trigger.attemptTriggerType(),
            null,
            effectiveIdempotencyKey);
    if (!executionRepository.insert(execution) || execution.getId() == null) {
      throw new IllegalStateException("创建离线同步执行实例失败");
    }

    batchRuntime.refreshBatch(batchId);
    return new OfflineExecutionClaim(
        definition,
        batch.snapshot().logicalJobSpec(),
        execution,
        false);
  }

  private Long createBatch(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      Parsed trigger,
      String idempotencyKey) {
    BatchScope scope = incrementalPlanner.plan(definitionId, definitionSnapshotJson);
    BatchTrigger batchTrigger =
        Objects.requireNonNull(trigger.batchTrigger(), "初始执行必须包含 BatchTrigger");
    BatchKey batchKey = trigger.batchKey();
    if (batchKey == null) {
      batchKey = defaultBatchKey(batchTrigger, idempotencyKey, scope);
    }

    RetryPolicySnapshot retryPolicy = freezeRetryPolicy(definitionId);
    ExecutionSnapshot snapshot =
        new ExecutionSnapshot(
            requireText(definitionSnapshotJson, "definitionSnapshot 不能为空"),
            (int) Math.min(Integer.MAX_VALUE, Math.max(1L, definitionVersion)),
            retryPolicy,
            requireText(configDigest, "configDigest 不能为空"),
            requireText(logicalJobSpecJson, "logicalJobSpec 不能为空"));
    BatchExecution batch =
        new BatchExecution(
            null,
            definitionId,
            batchKey,
            batchTrigger,
            scope,
            snapshot,
            BatchStatus.PENDING,
            List.of());
    BatchExecution saved = batchRepository.insert(batch);
    if (saved.id() == null) {
      throw new IllegalStateException("创建 BatchExecution 后缺少 ID");
    }
    return saved.id();
  }

  private BatchKey defaultBatchKey(
      BatchTrigger trigger, String idempotencyKey, BatchScope scope) {
    return switch (trigger) {
      case MANUAL -> BatchKey.manual(idempotencyKey);
      case WORKFLOW -> BatchKey.workflow(idempotencyKey);
      case BACKFILL -> BatchKey.backfill(idempotencyKey, scope.fingerprint());
      case SCHEDULE ->
          throw new IllegalArgumentException("SCHEDULE trigger 必须携带 scheduleId + plannedFireTime");
    };
  }

  private RetryPolicySnapshot freezeRetryPolicy(Long definitionId) {
    OfflineSchedule schedule = scheduleRepository.findSchedule(definitionId);
    int maxAttempts =
        schedule == null
            ? properties.getControl().getDefaultMaxAttempts()
            : schedule.retryMaxAttempts();
    int backoffSeconds =
        schedule == null
            ? properties.getControl().getDefaultRetryBackoffSeconds()
            : schedule.retryBackoffSeconds();
    return new RetryPolicySnapshot(Math.max(1, maxAttempts), Math.max(0, backoffSeconds));
  }

  private BatchExecution validateIdempotentReuse(
      OfflineJobExecution existing,
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson) {
    Long batchId = existing.getBatchId();
    if (batchId == null || batchId <= 0L) {
      throw new IllegalStateException(
          "幂等键命中未绑定 Batch 的历史执行，仅支持查询：" + existing.getIdempotencyKey());
    }

    BatchExecution batch =
        batchRepository
            .findById(batchId)
            .orElseThrow(
                () -> new IllegalStateException("幂等 Attempt 绑定的 BatchExecution 不存在：" + batchId));
    ExecutionSnapshot snapshot = batch.snapshot();
    int version = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, definitionVersion));
    boolean same =
        Objects.equals(existing.getJobDefinitionId(), batch.taskId())
            && Objects.equals(batch.taskId(), definitionId)
            && snapshot.definitionRevision() == version
            && Objects.equals(snapshot.configDigest(), configDigest)
            && Objects.equals(snapshot.definitionSnapshot(), definitionSnapshotJson)
            && Objects.equals(snapshot.logicalJobSpec(), logicalJobSpecJson);
    if (!same) {
      throw new IllegalStateException("幂等键已被不同 Batch 快照占用：" + existing.getIdempotencyKey());
    }
    return batch;
  }

  private String normalizeOptionalIdempotencyKey(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(message);
    }
    return value.trim();
  }
}
