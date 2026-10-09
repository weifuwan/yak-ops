package io.yak.ops.business.datasync.execution.lifecycle;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.execution.executor.OfflineSyncExecutor;
import io.yak.ops.business.datasync.execution.executor.RealtimeSyncExecutor;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * 收口单节点 Data Sync 应用启动恢复语义。
 *
 * <p>PENDING / RUNNING 对应的 LocalExecution 无法跨进程恢复，启动时仍标记为 LOST。
 * RETRY_WAITING 已持久化 currentAttempt / nextRetryTime / definitionSnapshot，不再丢弃原 Execution；
 * 应用启动后从下一个可用 Attempt 序号继续同一 Retry Chain。</p>
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
@DependsOn("yakOpsFlyway")
public class DataSyncExecutionRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncExecutionRecovery.class);

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncAttemptRepository attemptRepository;

    @Resource
    private DataSyncAttemptLifecycle attemptLifecycle;

    @Resource
    private DataSyncTableAttemptLifecycle tableAttemptLifecycle;

    @Resource
    private OfflineSyncExecutor offlineSyncExecutor;

    @Resource
    private RealtimeSyncExecutor realtimeSyncExecutor;

    public void recoverExecutions() {
        List<DataSyncInstanceEntity> activeExecutions = instanceRepository.queryActive();
        LocalDateTime now = DateUtils.now();

        int attempts = attemptRepository.markActiveAsLost(
                now, DataSyncErrorCode.EXECUTION_LOST.getCode(), DataSyncErrorCode.EXECUTION_LOST.getMessage());
        int executions = instanceRepository.markActiveAsLost(
                now, DataSyncErrorCode.EXECUTION_LOST.getCode(), DataSyncErrorCode.EXECUTION_LOST.getMessage());

        for (DataSyncInstanceEntity execution : activeExecutions) {
            if (!runtimeExecution(execution)) continue;
            if (isMultiTable(execution)) {
                tableAttemptLifecycle.cancelUnfinished(
                        execution.getWorkspaceId(), execution.getId(), DataSyncTableExecutionStatus.LOST);
            }
            attemptLifecycle.recordExecutionLost(
                    execution.getWorkspaceId(), execution.getId(), "应用启动发现旧进程遗留 Runtime Execution，已标记为 LOST");
        }

        int retryScheduled = 0;
        int retryLost = 0;
        for (DataSyncInstanceEntity execution : activeExecutions) {
            if (execution.getStatus() != DataSyncInstanceStatus.RETRY_WAITING) continue;
            if (recoverRetryWaiting(execution)) retryScheduled++;
            else retryLost++;
        }

        if (executions > 0 || attempts > 0 || retryScheduled > 0 || retryLost > 0) {
            LOG.warn(
                    "数据同步启动恢复完成，lostExecutions={}, lostAttempts={}, retryScheduled={}, retryLost={}",
                    executions,
                    attempts,
                    retryScheduled,
                    retryLost);
        }
    }

    private boolean recoverRetryWaiting(DataSyncInstanceEntity execution) {
        try {
            if (execution.getNextRetryTime() == null) {
                throw new IllegalStateException("RETRY_WAITING 缺少 nextRetryTime");
            }
            if (StringUtils.isBlank(execution.getDefinitionSnapshot())) {
                throw new IllegalStateException("RETRY_WAITING 缺少 definitionSnapshot");
            }
            if (execution.getSyncType() == null) {
                throw new IllegalStateException("RETRY_WAITING 缺少 syncType");
            }

            DataSyncDefinitionSnapshotVO snapshot =
                    JSONUtils.parseObject(execution.getDefinitionSnapshot(), DataSyncDefinitionSnapshotVO.class);
            int maxAttempts = Math.max(1, execution.getMaxAttempts() == null ? 1 : execution.getMaxAttempts());
            int backoffSeconds =
                    Math.max(0, execution.getBackoffSeconds() == null ? 60 : execution.getBackoffSeconds());
            int currentAttempt = Math.max(1, execution.getCurrentAttempt() == null ? 1 : execution.getCurrentAttempt());
            int nextAttemptNo = Math.max(currentAttempt, highestAttemptNo(execution)) + 1;

            if (nextAttemptNo > maxAttempts) {
                throw new IllegalStateException(
                        "RETRY_WAITING 已没有可用 Attempt，nextAttempt=" + nextAttemptNo + ", maxAttempts=" + maxAttempts);
            }

            attemptLifecycle.recordRetryRecoveryScheduled(
                    execution.getWorkspaceId(), execution.getId(), nextAttemptNo, execution.getNextRetryTime());

            if (execution.getSyncType() == DataSyncType.OFFLINE) {
                offlineSyncExecutor.resumeRetry(
                        execution.getWorkspaceId(),
                        execution.getId(),
                        snapshot,
                        nextAttemptNo,
                        maxAttempts,
                        backoffSeconds,
                        execution.getNextRetryTime());
            } else if (execution.getSyncType() == DataSyncType.REALTIME) {
                realtimeSyncExecutor.resumeRetry(
                        execution.getWorkspaceId(),
                        execution.getId(),
                        snapshot,
                        nextAttemptNo,
                        maxAttempts,
                        backoffSeconds,
                        execution.getNextRetryTime());
            } else {
                throw new IllegalStateException("不支持恢复的同步类型：" + execution.getSyncType());
            }
            return true;
        } catch (RuntimeException exception) {
            markRetryRecoveryLost(execution, safeMessage(exception));
            return false;
        }
    }

    private int highestAttemptNo(DataSyncInstanceEntity execution) {
        return attemptRepository.queryByExecution(execution.getWorkspaceId(), execution.getId()).stream()
                .map(DataSyncAttemptEntity::getAttemptNo)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    private void markRetryRecoveryLost(DataSyncInstanceEntity execution, String detail) {
        int attemptNo = Math.max(1, execution.getCurrentAttempt() == null ? 1 : execution.getCurrentAttempt());
        boolean updated = instanceRepository.completeExecution(
                execution.getWorkspaceId(),
                execution.getId(),
                DataSyncInstanceStatus.RETRY_WAITING,
                DataSyncInstanceStatus.LOST,
                attemptNo,
                DateUtils.now(),
                execution.getReadRows() == null ? 0L : execution.getReadRows(),
                execution.getWriteRows() == null ? 0L : execution.getWriteRows(),
                DataSyncErrorCode.EXECUTION_LOST.getCode(),
                detail);
        if (updated) {
            attemptLifecycle.recordExecutionLost(
                    execution.getWorkspaceId(), execution.getId(), "应用启动无法恢复等待重试 Execution：" + detail);
        }
        LOG.warn(
                "等待重试Execution恢复失败，workspaceId={}, executionId={}, error={}",
                execution.getWorkspaceId(),
                execution.getId(),
                detail);
    }

    private boolean isMultiTable(DataSyncInstanceEntity execution) {
        if (execution.getSyncType() != DataSyncType.OFFLINE || StringUtils.isBlank(execution.getDefinitionSnapshot()))
            return false;
        try {
            DataSyncDefinitionSnapshotVO snapshot =
                    JSONUtils.parseObject(execution.getDefinitionSnapshot(), DataSyncDefinitionSnapshotVO.class);
            return snapshot.getTableRoutes() != null
                    && snapshot.getTableRoutes().size() > 1;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "检查多表快照失败，workspaceId={}, executionId={}, error={}",
                    execution.getWorkspaceId(),
                    execution.getId(),
                    safeMessage(exception));
            return false;
        }
    }

    private boolean runtimeExecution(DataSyncInstanceEntity execution) {
        return execution.getStatus() == DataSyncInstanceStatus.PENDING
                || execution.getStatus() == DataSyncInstanceStatus.RUNNING;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (StringUtils.isBlank(message)) {
            message = throwable == null
                    ? "Durable Retry Recovery 失败"
                    : throwable.getClass().getSimpleName();
        }
        return SensitiveUtils.mask(message);
    }
}
