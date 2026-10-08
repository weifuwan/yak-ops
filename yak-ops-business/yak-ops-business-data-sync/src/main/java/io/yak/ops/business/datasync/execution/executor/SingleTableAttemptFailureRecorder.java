package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryAssessment;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.flow.runtime.ExecutionMetrics;

/**
 * 单表执行器失败收口时，将共用指标和退避参数交给唯一的 Attempt Lifecycle 持久化。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
final class SingleTableAttemptFailureRecorder {

    private SingleTableAttemptFailureRecorder() {}

    static DataSyncRetryDecision record(
            DataSyncAttemptLifecycle lifecycle,
            SingleTableRunContext context,
            DataSyncAttemptEntity attempt,
            int attemptNo,
            AttemptFailureDetails details,
            DataSyncRetryAssessment assessment) {
        ExecutionMetrics metrics = details.metrics();
        long readRows = metrics == null ? 0L : metrics.readRows();
        long writeRows = metrics == null ? 0L : metrics.writeRows();
        int effectiveBackoffSeconds = ExecutionRetryPolicy.from(context.snapshot().getRetryPolicy())
                .delayForAttempt(attemptNo, context.backoffSeconds());
        return lifecycle.failAttempt(
                context.workspaceId(),
                context.executionId(),
                attempt.getId(),
                attemptNo,
                details.expectedAttemptStatus(),
                details.expectedExecutionStatus(),
                assessment.retryable(),
                assessment.reason(),
                context.maxAttempts(),
                effectiveBackoffSeconds,
                readRows,
                writeRows,
                DataSyncErrorCode.EXECUTION_FAILED.getCode(),
                details.message());
    }
}
