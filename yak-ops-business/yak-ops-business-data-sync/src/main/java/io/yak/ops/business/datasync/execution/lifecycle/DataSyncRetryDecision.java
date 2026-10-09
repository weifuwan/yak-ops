package io.yak.ops.business.datasync.execution.lifecycle;

import java.time.LocalDateTime;

/**
 * 表示一次 Attempt 失败后是否继续 Retry，以及下一次 Attempt 的计划时间。
 *
 * @param retry 是否继续 Retry
 * @param nextRetryTime 下一次 Attempt 计划时间；不 Retry 时为空
 * @author weifuwan
 * @since 2026-09-29
 */
public record DataSyncRetryDecision(boolean retry, LocalDateTime nextRetryTime) {

    public static DataSyncRetryDecision stop() {
        return new DataSyncRetryDecision(false, null);
    }

    public static DataSyncRetryDecision retryAt(LocalDateTime nextRetryTime) {
        return new DataSyncRetryDecision(true, nextRetryTime);
    }
}
