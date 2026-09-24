package io.yak.ops.business.datasource.exception;

import io.yak.ops.common.ErrorCode;
import io.yak.ops.common.exception.BusinessException;

/** 数据源管理业务异常。 */
public class DataSourceException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode actualErrorCode;
    private final String userMessage;

    public DataSourceException(ErrorCode errorCode) {
        super(errorCode);
        this.actualErrorCode = errorCode;
        this.userMessage = errorCode == null ? null : errorCode.getMessage();
    }

    public DataSourceException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public DataSourceException(ErrorCode errorCode, String detail, Throwable cause) {
        super(buildMessage(errorCode, detail), cause);
        this.actualErrorCode = errorCode;
        this.userMessage = buildMessage(errorCode, detail);
    }

    @Override
    public ErrorCode getErrorCode() {
        return actualErrorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    private static String buildMessage(ErrorCode errorCode, String detail) {
        String base = errorCode == null ? "数据源操作失败" : errorCode.getMessage();
        return detail == null || detail.trim().isEmpty() ? base : base + "：" + detail.trim();
    }
}
