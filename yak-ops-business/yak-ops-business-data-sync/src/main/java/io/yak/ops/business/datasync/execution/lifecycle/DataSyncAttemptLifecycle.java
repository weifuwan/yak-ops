package io.yak.ops.business.datasync.execution.lifecycle;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncExecutionEventLevel;
import io.yak.ops.common.enums.datasync.DataSyncExecutionEventType;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncExecutionEventEntity;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncExecutionEventRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一维护 Execution Root 与 child Attempt 的一致状态迁移、指标镜像和 Retry Waiting 语义。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Component
public class DataSyncAttemptLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncAttemptLifecycle.class);
    private static final int MAX_EVENT_MESSAGE_LENGTH = 1000;

    @Resource
    private DataSyncAttemptRepository attemptRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncExecutionEventRepository eventRepository;

    @Transactional(rollbackFor = Exception.class)
    public DataSyncAttemptEntity createAttempt(String workspaceId, String executionId, int attemptNo) {
        DataSyncAttemptEntity attempt = new DataSyncAttemptEntity();
        attempt.setWorkspaceId(workspaceId);
        attempt.setExecutionId(executionId);
        attempt.setAttemptNo(attemptNo);
        attempt.setStatus(DataSyncAttemptStatus.PENDING);
        attempt.setReadRows(0L);
        attempt.setWriteRows(0L);
        attempt.initCreate();
        if (attemptRepository.add(attempt) == null) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "创建 Attempt 失败");
        }
        return attempt;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean startAttempt(
            String workspaceId,
            String executionId,
            String attemptId,
            int attemptNo,
            DataSyncInstanceStatus expectedExecutionStatus) {
        LocalDateTime startTime = DateUtils.now();
        if (!instanceRepository.startAttempt(workspaceId, executionId, expectedExecutionStatus, attemptNo, startTime)) {
            attemptRepository.cancelActiveByExecution(workspaceId, executionId, DateUtils.now());
            return false;
        }
        if (!attemptRepository.transitionStatus(
                workspaceId,
                attemptId,
                DataSyncAttemptStatus.PENDING,
                DataSyncAttemptStatus.RUNNING,
                startTime,
                null,
                null,
                null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "启动 Attempt 失败");
        }
        if (attemptNo == 1) {
            appendEvent(
                    workspaceId,
                    executionId,
                    null,
                    DataSyncExecutionEventLevel.INFO,
                    DataSyncExecutionEventType.EXECUTION_STARTED,
                    "Execution 开始执行");
        }
        appendEvent(
                workspaceId,
                executionId,
                attemptId,
                DataSyncExecutionEventLevel.INFO,
                DataSyncExecutionEventType.ATTEMPT_STARTED,
                "Attempt #" + attemptNo + " 开始执行");
        return true;
    }

    public void updateMetrics(String workspaceId, String executionId, String attemptId, long readRows, long writeRows) {
        if (attemptRepository.updateMetrics(workspaceId, attemptId, readRows, writeRows)) {
            instanceRepository.updateMetrics(workspaceId, executionId, readRows, writeRows);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void succeedAttempt(
            String workspaceId, String executionId, String attemptId, int attemptNo, long readRows, long writeRows) {
        LocalDateTime finishTime = DateUtils.now();
        if (!attemptRepository.transitionStatus(
                workspaceId,
                attemptId,
                DataSyncAttemptStatus.RUNNING,
                DataSyncAttemptStatus.SUCCEEDED,
                null,
                finishTime,
                null,
                null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "完成 Attempt 成功状态失败");
        }
        if (!instanceRepository.completeExecution(
                workspaceId,
                executionId,
                DataSyncInstanceStatus.RUNNING,
                DataSyncInstanceStatus.SUCCEEDED,
                attemptNo,
                finishTime,
                readRows,
                writeRows,
                null,
                null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "完成 Execution 成功状态失败");
        }
        appendEvent(
                workspaceId,
                executionId,
                attemptId,
                DataSyncExecutionEventLevel.INFO,
                DataSyncExecutionEventType.ATTEMPT_SUCCEEDED,
                "Attempt #" + attemptNo + " 执行成功");
        appendEvent(
                workspaceId,
                executionId,
                null,
                DataSyncExecutionEventLevel.INFO,
                DataSyncExecutionEventType.EXECUTION_SUCCEEDED,
                "Execution 执行成功");
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSyncRetryDecision failAttempt(
            String workspaceId,
            String executionId,
            String attemptId,
            int attemptNo,
            DataSyncAttemptStatus expectedAttemptStatus,
            DataSyncInstanceStatus expectedExecutionStatus,
            boolean retryAllowed,
            String retryReason,
            int maxAttempts,
            int backoffSeconds,
            long readRows,
            long writeRows,
            Integer errorCode,
            String errorMessage) {
        DataSyncInstanceEntity execution =
                instanceRepository.queryById(workspaceId, executionId).orElse(null);
        if (execution == null) return DataSyncRetryDecision.stop();
        if (execution.getStatus() == DataSyncInstanceStatus.CANCELED) {
            attemptRepository.cancelActiveByExecution(workspaceId, executionId, DateUtils.now());
            return DataSyncRetryDecision.stop();
        }

        LocalDateTime finishTime = DateUtils.now();
        if (!attemptRepository.transitionStatus(
                workspaceId,
                attemptId,
                expectedAttemptStatus,
                DataSyncAttemptStatus.FAILED,
                null,
                finishTime,
                errorCode,
                errorMessage)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "记录 Attempt 失败状态失败");
        }
        appendEvent(
                workspaceId,
                executionId,
                attemptId,
                DataSyncExecutionEventLevel.ERROR,
                DataSyncExecutionEventType.ATTEMPT_FAILED,
                "Attempt #" + attemptNo + " 执行失败：" + safeEventMessage(errorMessage));

        if (retryAllowed && attemptNo < Math.max(1, maxAttempts)) {
            LocalDateTime nextRetryTime = finishTime.plusSeconds(Math.max(0, backoffSeconds));
            if (!instanceRepository.waitForRetry(
                    workspaceId,
                    executionId,
                    expectedExecutionStatus,
                    attemptNo,
                    nextRetryTime,
                    readRows,
                    writeRows,
                    errorCode,
                    errorMessage)) {
                return DataSyncRetryDecision.stop();
            }
            appendEvent(
                    workspaceId,
                    executionId,
                    attemptId,
                    DataSyncExecutionEventLevel.WARN,
                    DataSyncExecutionEventType.RETRY_WAITING,
                    "Attempt #" + attemptNo + " 失败，等待重试：" + safeEventMessage(retryReason));
            return DataSyncRetryDecision.retryAt(nextRetryTime);
        }

        if (!instanceRepository.completeExecution(
                workspaceId,
                executionId,
                expectedExecutionStatus,
                DataSyncInstanceStatus.FAILED,
                attemptNo,
                finishTime,
                readRows,
                writeRows,
                errorCode,
                errorMessage)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "记录 Execution 最终失败状态失败");
        }
        String finalMessage = "Execution 执行失败：" + safeEventMessage(errorMessage);
        if (!retryAllowed && attemptNo < Math.max(1, maxAttempts)) {
            finalMessage += "；未自动重试：" + safeEventMessage(retryReason);
        }
        appendEvent(
                workspaceId,
                executionId,
                null,
                DataSyncExecutionEventLevel.ERROR,
                DataSyncExecutionEventType.EXECUTION_FAILED,
                finalMessage);
        return DataSyncRetryDecision.stop();
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelActiveAttempt(String workspaceId, String executionId) {
        LocalDateTime finishTime = DateUtils.now();
        attemptRepository.cancelActiveByExecution(workspaceId, executionId, finishTime);
        DataSyncInstanceEntity execution =
                instanceRepository.queryById(workspaceId, executionId).orElse(null);
        if (execution == null
                || execution.getStatus() == null
                || execution.getStatus().isTerminal()) return;
        if (instanceRepository.cancelExecution(workspaceId, executionId, execution.getStatus(), finishTime)) {
            recordExecutionCanceled(workspaceId, executionId);
        }
    }

    public void recordSourceReady(String workspaceId, String executionId, String attemptId) {
        appendEvent(
                workspaceId,
                executionId,
                attemptId,
                DataSyncExecutionEventLevel.INFO,
                DataSyncExecutionEventType.SOURCE_READY,
                "来源执行计划已准备");
    }

    public void recordTargetReady(String workspaceId, String executionId, String attemptId) {
        appendEvent(
                workspaceId,
                executionId,
                attemptId,
                DataSyncExecutionEventLevel.INFO,
                DataSyncExecutionEventType.TARGET_READY,
                "目标执行计划已准备");
    }

    public void recordRetryRecoveryScheduled(
            String workspaceId, String executionId, int nextAttemptNo, LocalDateTime nextRetryTime) {
        appendEvent(
                workspaceId,
                executionId,
                null,
                DataSyncExecutionEventLevel.WARN,
                DataSyncExecutionEventType.RETRY_WAITING,
                "应用重启后恢复等待重试，将从 Attempt #" + nextAttemptNo + " 继续，计划时间=" + nextRetryTime);
    }

    public void recordAutoRecoveryStarted(String workspaceId, String executionId) {
        appendEvent(
                workspaceId,
                executionId,
                null,
                DataSyncExecutionEventLevel.INFO,
                DataSyncExecutionEventType.AUTO_RECOVERY_STARTED,
                "应用启动恢复已创建新的实时同步 Execution");
    }

    public void recordExecutionCanceled(String workspaceId, String executionId) {
        appendEvent(
                workspaceId,
                executionId,
                null,
                DataSyncExecutionEventLevel.INFO,
                DataSyncExecutionEventType.EXECUTION_CANCELED,
                "Execution 已取消");
    }

    public void recordExecutionLost(String workspaceId, String executionId, String message) {
        appendEvent(
                workspaceId,
                executionId,
                null,
                DataSyncExecutionEventLevel.WARN,
                DataSyncExecutionEventType.EXECUTION_LOST,
                safeEventMessage(message));
    }

    public boolean isRetryWaiting(String workspaceId, String executionId) {
        return instanceRepository
                .queryById(workspaceId, executionId)
                .map(value -> value.getStatus() == DataSyncInstanceStatus.RETRY_WAITING)
                .orElse(false);
    }

    private void appendEvent(
            String workspaceId,
            String executionId,
            String attemptId,
            DataSyncExecutionEventLevel level,
            DataSyncExecutionEventType eventType,
            String message) {
        try {
            DataSyncExecutionEventEntity event = new DataSyncExecutionEventEntity();
            event.setWorkspaceId(workspaceId);
            event.setExecutionId(executionId);
            event.setAttemptId(attemptId);
            event.setLevel(level);
            event.setEventType(eventType);
            event.setMessage(safeEventMessage(message));
            event.initCreate();
            eventRepository.add(event);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "数据同步产品事件记录失败，workspaceId={}, executionId={}, eventType={}, error={}",
                    workspaceId,
                    executionId,
                    eventType,
                    SensitiveUtils.mask(exception.getMessage()));
        }
    }

    private String safeEventMessage(String message) {
        String value = StringUtils.trimToNull(SensitiveUtils.mask(message));
        if (value == null) return "执行状态已更新";
        return value.length() > MAX_EVENT_MESSAGE_LENGTH ? value.substring(0, MAX_EVENT_MESSAGE_LENGTH) : value;
    }
}
