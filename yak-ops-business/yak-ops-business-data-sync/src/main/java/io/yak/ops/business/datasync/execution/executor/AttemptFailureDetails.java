package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;

/**
 * 单表 Attempt 失败收口所需的预期状态、Runtime 指标和异常信息。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
record AttemptFailureDetails(
        DataSyncAttemptStatus expectedAttemptStatus,
        DataSyncInstanceStatus expectedExecutionStatus,
        ExecutionMetrics metrics,
        Throwable cause,
        String message) {}
