package io.yak.ops.business.datasource.exception;

import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.ErrorCode;
import io.yak.ops.common.util.SensitiveUtils;

/**
 * Datasource Service 与 Plugin 边界统一使用的领域业务异常。
 *
 * <p>保留结构化 ErrorCode，并在生成用户可见错误消息时统一执行敏感信息遮罩。</p>
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public class DataSourceException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** 对外保留的结构化 Datasource 错误码。 */
    private final ErrorCode actualErrorCode;

    /** 已完成敏感信息遮罩、可安全返回给用户的错误消息。 */
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

    /** @return 已完成敏感信息遮罩的用户可见错误消息 */
    public String getUserMessage() {
        return userMessage;
    }

    private static String buildMessage(ErrorCode errorCode, String detail) {
        String base = errorCode == null ? "数据源操作失败" : errorCode.getMessage();
        String message = detail == null || detail.trim().isEmpty() ? base : base + "：" + detail.trim();
        return SensitiveUtils.mask(message);
    }
}
