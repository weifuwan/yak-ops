package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;

/**
 * Data Sync Executor 错误信息的脱敏和长度限制，保留单表与表级执行原有兜底文案。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
final class ExecutionErrorMessages {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final String TABLE_FAILURE_MESSAGE = "表级执行失败";

    private ExecutionErrorMessages() {}

    static String attemptFailure(Throwable throwable) {
        String fallback = DataSyncErrorCode.EXECUTION_FAILED.getMessage();
        String message = throwable == null ? fallback : throwable.getMessage();
        if (StringUtils.isBlank(message)) {
            message = throwable == null ? fallback : throwable.getClass().getSimpleName();
        }
        return sanitize(message, fallback);
    }

    static String tableFailure(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return tableFailure(StringUtils.isBlank(message) ? TABLE_FAILURE_MESSAGE : message);
    }

    static String tableFailure(String message) {
        return sanitize(message, TABLE_FAILURE_MESSAGE);
    }

    private static String sanitize(String message, String fallback) {
        String masked = SensitiveUtils.mask(message == null ? fallback : message);
        return masked.length() > MAX_ERROR_MESSAGE_LENGTH ? masked.substring(0, MAX_ERROR_MESSAGE_LENGTH) : masked;
    }
}
