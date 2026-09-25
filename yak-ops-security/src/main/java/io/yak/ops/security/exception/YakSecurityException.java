package io.yak.ops.security.exception;

import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.ErrorCode;
import java.io.Serial;

/**
 * Security 领域业务异常，复用 Common 的结构化业务异常契约。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public class YakSecurityException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public YakSecurityException() {
        super((String) null);
    }

    public YakSecurityException(ErrorCode errorCode) {
        super(errorCode);
    }

    public YakSecurityException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public YakSecurityException(String message) {
        super(message);
    }

    public YakSecurityException(String message, Throwable cause) {
        super(message, cause);
    }

    public YakSecurityException(Throwable cause) {
        super(cause);
    }
}
