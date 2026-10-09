package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;

/**
 * Executor Contract 测试使用的记录型 Attempt 生命周期替身，不执行数据库操作。
 */
final class ExecutorAttemptLifecycleProbe extends DataSyncAttemptLifecycle {

    boolean retryWaiting = true;
    int retryChecks;
    String workspaceId;
    String executionId;
    String attemptId;
    int attemptNo;
    int maxAttempts;
    int backoffSeconds;
    long readRows;
    long writeRows;
    boolean retryAllowed;
    String retryReason;
    String errorMessage;
    Integer errorCode;
    DataSyncAttemptStatus expectedAttemptStatus;
    DataSyncInstanceStatus expectedExecutionStatus;

    @Override
    public boolean isRetryWaiting(String workspaceId, String executionId) {
        this.workspaceId = workspaceId;
        this.executionId = executionId;
        retryChecks++;
        return retryWaiting;
    }

    @Override
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
        this.workspaceId = workspaceId;
        this.executionId = executionId;
        this.attemptId = attemptId;
        this.attemptNo = attemptNo;
        this.expectedAttemptStatus = expectedAttemptStatus;
        this.expectedExecutionStatus = expectedExecutionStatus;
        this.retryAllowed = retryAllowed;
        this.retryReason = retryReason;
        this.maxAttempts = maxAttempts;
        this.backoffSeconds = backoffSeconds;
        this.readRows = readRows;
        this.writeRows = writeRows;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        return DataSyncRetryDecision.stop();
    }
}
