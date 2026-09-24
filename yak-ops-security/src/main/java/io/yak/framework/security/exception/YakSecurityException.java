package io.yak.framework.security.exception;

import io.yak.framework.common.BusinessException;
import io.yak.framework.common.ErrorCode;

import java.io.Serial;

/** 安全模块业务异常；通用行为由 {@link BusinessException} 提供。 */
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
