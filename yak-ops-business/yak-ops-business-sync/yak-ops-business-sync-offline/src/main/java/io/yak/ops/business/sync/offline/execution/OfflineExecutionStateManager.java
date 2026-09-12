package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Owns Attempt state application, retry timing, events and Task last-* projection. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineExecutionStateManager {

  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineExecutionEventRepository eventRepository;
  private final OfflineBatchRuntime batchRuntime;
  private final ObjectMapper objectMapper;

  public OfflineExecutionStateManager(
      OfflineJobDefinitionRepository definitionRepository,
      OfflineJobExecutionRepository executionRepository,
      OfflineBatchExecutionRepository batchRepository,
      OfflineExecutionEventRepository eventRepository,
      OfflineBatchRuntime batchRuntime,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.definitionRepository = definitionRepository;
    this.executionRepository = executionRepository;
    this.batchRepository = batchRepository;
    this.eventRepository = eventRepository;
    this.batchRuntime = batchRuntime;
    this.objectMapper = objectMapper;
  }

  public void recordCreated(OfflineJobExecution execution) {
    record(
        execution,
        null,
        execution.getStatus(),
        "EXECUTION_CREATED",
        "使用 application.yml 中的固定 Link-Up 地址",
        null);
  }

  public void bindWorker(OfflineJobExecution execution, String instanceId) {
    execution.setWorkerInstanceId(instanceId);
    execution.setUpdateTime(LocalDateTime.now());
    batchRuntime.persistAttempt(execution);
  }

  public void markSubmitting(OfflineJobExecution execution) {
    transition(
        execution,
        OfflineExecutionStatus.SUBMITTED,
        "SUBMITTING",
        "正在向 Link-Up 提交 JobSpec",
        null);
  }

  public void markSubmitUncertain(OfflineJobExecution execution, String message) {
    String previous = execution.getStatus();
    execution.setStatus(OfflineExecutionStatus.UNKNOWN.name());
    execution.setErrorMessage(message);
    execution.setNextRetryTime(null);
    execution.setEndTime(null);
    execution.setLastSyncTime(LocalDateTime.now());
    execution.setUpdateTime(LocalDateTime.now());
    batchRuntime.persistAttempt(execution);
    projectTaskLastState(execution, OfflineExecutionStatus.UNKNOWN.name());
    record(
        execution,
        previous,
        OfflineExecutionStatus.UNKNOWN.name(),
        "SUBMIT_UNCERTAIN",
        message,
        null);
  }

  public void markFailed(OfflineJobExecution execution, String message, boolean retryable) {
    markTerminal(execution, OfflineExecutionStatus.FAILED, message, null, retryable);
  }

  /** Completes a frozen empty incremental batch locally without creating a Link-Up job. */
  public void markNoDataSucceeded(OfflineJobExecution execution) {
    LocalDateTime now = LocalDateTime.now();
    String previous = execution.getStatus();
    execution.setStartTime(now);
    execution.setDurationMillis(0L);
    execution.setSourceRecordCount(0L);
    execution.setSinkAttemptedRecordCount(0L);
    execution.setSinkSuccessRecordCount(0L);
    execution.setSinkCommittedRecordCount(0L);
    execution.setSourceReadBytes(0L);
    execution.setSinkWrittenBytes(0L);
    execution.setFailedRecordCount(0L);
    execution.setSkippedRecordCount(0L);
    execution.setQps(0D);
    execution.setStatus(OfflineExecutionStatus.SUCCEEDED.name());
    execution.setStateVersion(value(execution.getStateVersion(), 0L) + 1L);
    execution.setErrorMessage(null);
    execution.setEndTime(now);
    execution.setLastSyncTime(now);
    execution.setUpdateTime(now);
    configureRetry(execution, OfflineExecutionStatus.SUCCEEDED, false);
    batchRuntime.persistAttempt(execution);
    projectTaskLastState(execution, OfflineExecutionStatus.SUCCEEDED.name());
    record(
        execution,
        previous,
        OfflineExecutionStatus.SUCCEEDED.name(),
        "NO_DATA_SUCCEEDED",
        "来源游标无变化，本批次无需同步",
        null);
  }

  public void markCancellationRequested(OfflineJobExecution execution) {
    execution.setCancellationRequested(true);
    execution.setUpdateTime(LocalDateTime.now());
    batchRuntime.persistAttempt(execution);
    record(
        execution,
        execution.getStatus(),
        execution.getStatus(),
        "CANCEL_REQUESTED",
        "Yak Ops 已记录取消意图",
        null);
  }

  public void markWaitingRetryCanceled(OfflineJobExecution execution) {
    projectTaskLastState(execution, BatchStatus.CANCELED.name());
    record(
        execution,
        execution.getStatus(),
        execution.getStatus(),
        "BATCH_CANCELLED_WAITING_RETRY",
        "已取消 Batch 的 Retry 等待，不再创建新的 Attempt",
        null);
  }

  public void applySnapshot(
      OfflineJobExecution execution,
      LinkUpJobResponse response,
      String eventType) {
    if (execution == null || response == null) {
      return;
    }
    requireRuntimeBatch(execution, "对账");

    String previous = execution.getStatus();
    OfflineExecutionStatus next =
        StringUtils.hasText(response.getStatus())
            ? OfflineExecutionStatus.parse(response.getStatus())
            : OfflineExecutionStatus.parse(execution.getStatus());

    execution.setEngineJobId(first(response.getJobId(), execution.getEngineJobId()));
    execution.setWorkerInstanceId(
        first(response.getWorkerInstanceId(), execution.getWorkerInstanceId()));
    execution.setStatus(next.name());
    execution.setStateVersion(
        Math.max(value(execution.getStateVersion(), 0L), value(response.getStateVersion(), 0L)));
    execution.setCancellationRequested(
        Boolean.TRUE.equals(response.getCancellationRequested())
            || Boolean.TRUE.equals(execution.getCancellationRequested()));
    execution.setEngineSnapshotJson(write(response));
    execution.setErrorMessage(response.getErrorMessage());
    applyMetrics(execution, response);
    execution.setDurationMillis(value(response.getDurationMillis(), 0L));
    execution.setStartTime(time(response.getStartTimeMillis()));
    execution.setEndTime(time(response.getEndTimeMillis()));
    execution.setLastSyncTime(LocalDateTime.now());
    execution.setUpdateTime(LocalDateTime.now());
    configureRetry(execution, next, retryable(response, next));

    batchRuntime.persistAttempt(execution);
    projectTaskLastState(execution, next.name());
    if (!next.name().equals(previous)) {
      record(
          execution,
          previous,
          next.name(),
          eventType,
          response.getErrorMessage(),
          execution.getEngineSnapshotJson());
    }
  }

  public void markUnknown(OfflineJobExecution execution, String message) {
    if (execution == null || !OfflineExecutionStatus.isActive(execution.getStatus())) {
      return;
    }
    requireRuntimeBatch(execution, "UNKNOWN 对账");
    if (OfflineExecutionStatus.UNKNOWN.name().equalsIgnoreCase(execution.getStatus())) {
      return;
    }

    String previous = execution.getStatus();
    execution.setStatus(OfflineExecutionStatus.UNKNOWN.name());
    execution.setStateVersion(value(execution.getStateVersion(), 0L) + 1L);
    execution.setErrorMessage(message);
    execution.setNextRetryTime(null);
    execution.setEndTime(null);
    execution.setLastSyncTime(LocalDateTime.now());
    execution.setUpdateTime(LocalDateTime.now());
    batchRuntime.persistAttempt(execution);
    projectTaskLastState(execution, OfflineExecutionStatus.UNKNOWN.name());
    record(
        execution,
        previous,
        OfflineExecutionStatus.UNKNOWN.name(),
        "UNKNOWN",
        message,
        null);
  }

  private void markTerminal(
      OfflineJobExecution execution,
      OfflineExecutionStatus status,
      String message,
      String payload,
      boolean retryable) {
    String previous = execution.getStatus();
    execution.setStatus(status.name());
    execution.setStateVersion(value(execution.getStateVersion(), 0L) + 1L);
    execution.setErrorMessage(message);
    execution.setEndTime(LocalDateTime.now());
    execution.setLastSyncTime(LocalDateTime.now());
    execution.setUpdateTime(LocalDateTime.now());
    configureRetry(execution, status, retryable);
    batchRuntime.persistAttempt(execution);
    projectTaskLastState(execution, status.name());
    record(execution, previous, status.name(), status.name(), message, payload);
  }

  private void transition(
      OfflineJobExecution execution,
      OfflineExecutionStatus target,
      String type,
      String message,
      String payload) {
    String previous = execution.getStatus();
    execution.setStatus(target.name());
    execution.setStateVersion(value(execution.getStateVersion(), 0L) + 1L);
    execution.setUpdateTime(LocalDateTime.now());
    batchRuntime.persistAttempt(execution);
    record(execution, previous, target.name(), type, message, payload);
  }

  private void applyMetrics(OfflineJobExecution execution, LinkUpJobResponse response) {
    JsonNode metrics = response.getMetrics();
    JsonNode commitSummary = response.getCommitSummary();
    long sourceRecordCount = number(metrics, "sourceRecordCount", 0L);
    long sinkAttemptedRecordCount = number(metrics, "sinkAttemptedRecordCount", 0L);
    long sinkSuccessRecordCount = number(metrics, "sinkSuccessRecordCount", 0L);
    long sinkCommittedRecordCount =
        number(commitSummary, "successfullyCommittedRecordCount", sinkSuccessRecordCount);
    double sourceAverageQps = decimal(metrics, "sourceAverageQps", 0D);
    double sinkAverageQps = decimal(metrics, "sinkAverageQps", 0D);

    execution.setSourceRecordCount(sourceRecordCount);
    execution.setSinkAttemptedRecordCount(sinkAttemptedRecordCount);
    execution.setSinkSuccessRecordCount(sinkSuccessRecordCount);
    execution.setSinkCommittedRecordCount(sinkCommittedRecordCount);
    execution.setSourceReadBytes(number(metrics, "sourceReadBytes", 0L));
    execution.setSinkWrittenBytes(number(metrics, "sinkWrittenBytes", 0L));
    execution.setSourceAverageQps(sourceAverageQps);
    execution.setSinkAverageQps(sinkAverageQps);
    execution.setFailedRecordCount(number(metrics, "failedRecordCount", 0L));
    execution.setSkippedRecordCount(number(metrics, "skippedRecordCount", 0L));
    execution.setDatabaseCommitMillis(number(metrics, "databaseCommitMillis", 0L));
    execution.setSqlExecutionMillis(number(metrics, "sqlExecutionMillis", 0L));
    execution.setQps(sourceAverageQps > 0D ? sourceAverageQps : sinkAverageQps);
  }

  private void configureRetry(
      OfflineJobExecution execution,
      OfflineExecutionStatus status,
      boolean retryable) {
    execution.setNextRetryTime(null);
    if (!retryable || status != OfflineExecutionStatus.FAILED) {
      return;
    }

    RetryPolicySnapshot retryPolicy = frozenRetryPolicy(execution);
    if (retryPolicy == null) {
      return;
    }
    if (value(execution.getAttemptNo(), 1) < retryPolicy.maxAttempts()) {
      execution.setNextRetryTime(
          LocalDateTime.now().plusSeconds(Math.max(0, retryPolicy.backoffSeconds())));
    }
  }

  private RetryPolicySnapshot frozenRetryPolicy(OfflineJobExecution execution) {
    Long batchId = execution.getBatchId();
    if (batchId == null || batchId <= 0L) {
      return null;
    }
    BatchExecution batch = batchRepository.findById(batchId).orElse(null);
    if (batch == null || !Objects.equals(execution.getJobDefinitionId(), batch.taskId())) {
      return null;
    }
    return batch.snapshot().retryPolicy();
  }

  private void projectTaskLastState(OfflineJobExecution execution, String fallbackStatus) {
    Long batchId = execution.getBatchId();
    if (batchId == null || batchId <= 0L) {
      return;
    }
    if (!isLatestAttempt(execution, batchId)) {
      return;
    }

    BatchExecution batch = batchRepository.findById(batchId).orElse(null);
    String projectedStatus = batch == null ? fallbackStatus : batch.status().name();
    OfflineJobDefinition definition =
        definitionRepository.findById(execution.getJobDefinitionId()).orElse(null);
    if (definition == null) {
      return;
    }

    definition.setLastExecutionId(execution.getId());
    definition.setLastEngineJobId(execution.getEngineJobId());
    definition.setLastJobStatus(projectedStatus);
    definition.setLastErrorMessage(execution.getErrorMessage());
    definition.setLastDurationMillis(execution.getDurationMillis());
    definition.setLastReadRowCount(execution.getSourceRecordCount());
    definition.setLastQps(execution.getQps());
    definition.setLastSyncBytes(
        Math.max(
            value(execution.getSourceReadBytes(), 0L),
            value(execution.getSinkWrittenBytes(), 0L)));
    definition.setLastStartTime(execution.getStartTime());
    definition.setLastEndTime(execution.getEndTime());
    definition.setUpdateTime(LocalDateTime.now());
    definitionRepository.update(definition);
  }

  private boolean isLatestAttempt(OfflineJobExecution execution, Long batchId) {
    List<OfflineJobExecution> attempts = executionRepository.findByBatchId(batchId);
    if (attempts.isEmpty()) {
      return true;
    }

    OfflineJobExecution latest =
        attempts.stream()
            .max(
                Comparator.comparingInt(
                        (OfflineJobExecution value) -> value(value.getAttemptNo(), 1))
                    .thenComparingLong(value -> value(value.getId(), 0L)))
            .orElseThrow();
    return Objects.equals(latest.getId(), execution.getId());
  }

  private Long requireRuntimeBatch(OfflineJobExecution execution, String operation) {
    Long batchId = execution.getBatchId();
    if (batchId == null || batchId <= 0L) {
      throw new IllegalStateException(
          "历史执行未绑定 Batch，仅支持查询，不能参与" + operation);
    }
    return batchId;
  }

  private boolean retryable(LinkUpJobResponse response, OfflineExecutionStatus status) {
    if (status != OfflineExecutionStatus.FAILED) {
      return false;
    }
    String code = response == null ? null : response.getErrorCode();
    if (!StringUtils.hasText(code)) {
      return true;
    }
    String normalized = code.toUpperCase(Locale.ROOT);
    return !(normalized.contains("CONFIG")
        || normalized.contains("VALIDATION")
        || normalized.contains("IDEMPOTENCY")
        || normalized.contains("BAD_REQUEST")
        || normalized.contains("UNSUPPORTED"));
  }

  private void record(
      OfflineJobExecution execution,
      String from,
      String to,
      String type,
      String message,
      String payload) {
    eventRepository.append(
        OfflineExecutionEvent.builder()
            .executionId(execution.getId())
            .stateVersion(value(execution.getStateVersion(), 0L))
            .fromStatus(from)
            .toStatus(to)
            .eventType(type)
            .message(message)
            .payloadJson(payload)
            .createTime(LocalDateTime.now())
            .build());
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化 Link-Up 执行快照失败", exception);
    }
  }

  private long number(JsonNode node, String field, long fallback) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || !value.isNumber() ? fallback : value.asLong(fallback);
  }

  private double decimal(JsonNode node, String field, double fallback) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || !value.isNumber() ? fallback : value.asDouble(fallback);
  }

  private LocalDateTime time(Long millis) {
    return millis == null || millis <= 0L
        ? null
        : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
  }

  private String first(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private int value(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private long value(Long value, long fallback) {
    return value == null ? fallback : value;
  }
}
