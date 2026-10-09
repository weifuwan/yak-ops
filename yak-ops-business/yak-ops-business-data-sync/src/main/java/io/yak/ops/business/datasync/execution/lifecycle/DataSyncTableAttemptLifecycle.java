package io.yak.ops.business.datasync.execution.lifecycle;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.entity.datasync.DataSyncTableAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTableAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Table Execution 内独立 Attempt 的唯一产品状态迁移 owner。
 *
 * <p>新 Attempt 只能从 PLANNED / RETRY_WAITING 的 Table Execution 开始，不能复活成功表。
 * Attempt 与 Table Execution 的终态必须在同一数据库事务内提交。</p>
 *
 * @author weifuwan
 * @since 2026-10-08
 */
@Component
public class DataSyncTableAttemptLifecycle {

    @Resource
    private DataSyncTableAttemptRepository tableAttemptRepository;

    @Resource
    private DataSyncTableExecutionRepository tableExecutionRepository;

    @Transactional(rollbackFor = Exception.class)
    public DataSyncTableAttemptEntity begin(String workspaceId, String tableExecutionId, int attemptNo) {
        if (attemptNo < 1) throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED);
        DataSyncTableExecutionStatus expected =
                attemptNo == 1 ? DataSyncTableExecutionStatus.PLANNED : DataSyncTableExecutionStatus.RETRY_WAITING;
        if (attemptNo == 1
                && !tableExecutionRepository.transition(
                        workspaceId,
                        tableExecutionId,
                        expected,
                        DataSyncTableExecutionStatus.PENDING,
                        0,
                        0L,
                        0L,
                        null,
                        null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "表级执行不允许启动");
        }
        DataSyncTableExecutionStatus beginStatus =
                attemptNo == 1 ? DataSyncTableExecutionStatus.PENDING : DataSyncTableExecutionStatus.RETRY_WAITING;
        if (!tableExecutionRepository.transition(
                workspaceId,
                tableExecutionId,
                beginStatus,
                DataSyncTableExecutionStatus.RUNNING,
                attemptNo,
                0L,
                0L,
                null,
                null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "表级执行状态已变化");
        }

        DataSyncTableAttemptEntity attempt = new DataSyncTableAttemptEntity();
        attempt.setWorkspaceId(workspaceId);
        attempt.setTableExecutionId(tableExecutionId);
        attempt.setAttemptNo(attemptNo);
        attempt.setStatus(DataSyncAttemptStatus.PENDING);
        attempt.setReadRows(0L);
        attempt.setWriteRows(0L);
        attempt.initCreate();
        if (tableAttemptRepository.add(attempt) == null
                || !tableAttemptRepository.transition(
                        workspaceId,
                        attempt.getId(),
                        DataSyncAttemptStatus.PENDING,
                        DataSyncAttemptStatus.RUNNING,
                        DateUtils.now(),
                        null,
                        null,
                        null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "创建表级 Attempt 失败");
        }
        return attempt;
    }

    public void updateMetrics(
            String workspaceId,
            String tableExecutionId,
            String attemptId,
            int attemptNo,
            long readRows,
            long writeRows) {
        if (!tableAttemptRepository.updateMetrics(workspaceId, attemptId, readRows, writeRows)) return;
        tableExecutionRepository.updateMetrics(workspaceId, tableExecutionId, attemptNo, readRows, writeRows);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(
            String workspaceId,
            String tableExecutionId,
            String attemptId,
            int attemptNo,
            long readRows,
            long writeRows,
            DataSyncTableExecutionStatus target,
            Integer errorCode,
            String errorMessage) {
        if (target != DataSyncTableExecutionStatus.SUCCEEDED
                && target != DataSyncTableExecutionStatus.FAILED
                && target != DataSyncTableExecutionStatus.RETRY_WAITING) {
            throw new IllegalArgumentException("invalid Table Attempt outcome: " + target);
        }
        updateMetrics(workspaceId, tableExecutionId, attemptId, attemptNo, readRows, writeRows);
        DataSyncAttemptStatus attemptStatus = target == DataSyncTableExecutionStatus.SUCCEEDED
                ? DataSyncAttemptStatus.SUCCEEDED
                : DataSyncAttemptStatus.FAILED;
        if (!tableAttemptRepository.transition(
                workspaceId,
                attemptId,
                DataSyncAttemptStatus.RUNNING,
                attemptStatus,
                null,
                DateUtils.now(),
                errorCode,
                errorMessage)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "表级 Attempt 终态竞争失败");
        }
        if (!tableExecutionRepository.transition(
                workspaceId,
                tableExecutionId,
                DataSyncTableExecutionStatus.RUNNING,
                target,
                attemptNo,
                readRows,
                writeRows,
                errorCode,
                errorMessage)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "表级 Execution 终态竞争失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelUnfinished(String workspaceId, String rootExecutionId, DataSyncTableExecutionStatus target) {
        if (target != DataSyncTableExecutionStatus.CANCELED && target != DataSyncTableExecutionStatus.LOST) {
            throw new IllegalArgumentException("only CANCELED / LOST are valid for unfinished table executions");
        }
        LocalDateTime now = DateUtils.now();
        for (DataSyncTableExecutionEntity table :
                tableExecutionRepository.queryByExecution(workspaceId, rootExecutionId)) {
            if (table.getStatus() != null && !table.getStatus().isTerminal()) {
                if (target == DataSyncTableExecutionStatus.LOST) {
                    tableAttemptRepository.markActiveAsLost(
                            workspaceId, table.getId(), now, DataSyncErrorCode.EXECUTION_LOST.getCode(), "执行进程所有权丢失");
                } else {
                    tableAttemptRepository.cancelActive(workspaceId, table.getId(), now);
                }
            }
        }
        tableExecutionRepository.finishUnfinished(
                workspaceId,
                rootExecutionId,
                target,
                now,
                target == DataSyncTableExecutionStatus.LOST ? DataSyncErrorCode.EXECUTION_LOST.getCode() : null,
                target == DataSyncTableExecutionStatus.LOST ? "执行进程所有权丢失" : null);
    }
}
