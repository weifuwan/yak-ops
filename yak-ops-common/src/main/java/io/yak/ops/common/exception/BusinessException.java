package io.yak.ops.common.exception;

import io.yak.ops.common.ErrorCode;

/**
 * 跨模块业务异常，保留结构化错误码以避免从异常文本反向解析。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(formatMessage(errorCode), cause);
        this.errorCode = errorCode;
    }

    public BusinessException(String message) {
        super(message);
        this.errorCode = null;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public BusinessException(Throwable cause) {
        super(cause);
        this.errorCode = null;
    }

    private static String formatMessage(ErrorCode errorCode) {
        if (errorCode == null) {
            return null;
        }
        return errorCode.getCode() + "-" + errorCode.getMessage();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
