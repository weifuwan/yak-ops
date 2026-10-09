package io.yak.ops.business.datasync.execution.lifecycle;

import java.util.Objects;

/**
 * 一次失败是否适合自动 Retry 的分类结果。
 *
 * @param retryable 是否允许进入 Retry Waiting
 * @param reason 面向执行日志的稳定判定原因
 * @author weifuwan
 * @since 2026-10-06
 */
public record DataSyncRetryAssessment(boolean retryable, String reason) {

    public DataSyncRetryAssessment {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public static DataSyncRetryAssessment retryable(String reason) {
        return new DataSyncRetryAssessment(true, reason);
    }

    public static DataSyncRetryAssessment stop(String reason) {
        return new DataSyncRetryAssessment(false, reason);
    }
}
