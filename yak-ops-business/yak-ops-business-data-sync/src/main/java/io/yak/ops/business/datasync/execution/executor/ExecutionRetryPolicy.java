package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;

/**
 * 执行器统一使用的冻结 Retry 参数默认值与 SMART 等待时长计算，不负责失败分类和状态迁移。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
record ExecutionRetryPolicy(DataSyncRetryPolicyMode mode, int maxAttempts, int baseBackoffSeconds) {

    private static final int MAX_SMART_BACKOFF_SECONDS = 300;

    static ExecutionRetryPolicy from(DataSyncRetryPolicyVO policy) {
        DataSyncRetryPolicyMode mode = policy == null ? null : policy.getMode();
        int maxAttempts = policy == null || policy.getMaxAttempts() == null ? 1 : Math.max(1, policy.getMaxAttempts());
        int backoffSeconds =
                policy == null || policy.getBackoffSeconds() == null ? 60 : Math.max(0, policy.getBackoffSeconds());
        return new ExecutionRetryPolicy(mode, maxAttempts, backoffSeconds);
    }

    boolean isFixed() {
        return mode == null || mode == DataSyncRetryPolicyMode.FIXED;
    }

    int delayForAttempt(int attemptNo, int configuredBackoffSeconds) {
        int base = Math.max(0, configuredBackoffSeconds);
        if (mode != DataSyncRetryPolicyMode.SMART) return base;
        long multiplier = 1L << Math.min(4, Math.max(0, attemptNo - 1));
        return (int) Math.min(MAX_SMART_BACKOFF_SECONDS, (long) base * multiplier);
    }
}
