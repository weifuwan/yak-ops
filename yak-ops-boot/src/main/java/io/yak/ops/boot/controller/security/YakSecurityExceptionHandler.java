package io.yak.ops.boot.controller.security;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.ops.boot.controller.security.v1.LoginController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** User/login HTTP exception mapping. */
@ConditionalOnProperty(
    prefix = "yak.security",
    name = {"database-enabled", "web-enabled"},
    havingValue = "true",
    matchIfMissing = true)
@RestControllerAdvice(basePackageClasses = LoginController.class)
public class YakSecurityExceptionHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(YakSecurityExceptionHandler.class);

  @ExceptionHandler(YakSecurityException.class)
  public ResponseEntity<Result<Void>> handleYakSecurityException(
      YakSecurityException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    Result<Void> body = errorCode == null
        ? Result.fail(exception)
        : Result.fail(errorCode.getCode(), errorCode.getMessage());
    HttpStatus status = resolveStatus(errorCode);
    if (status.is5xxServerError()) {
      LOGGER.error("Yak Security request failed", exception);
    }
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      BindException.class,
      MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class,
      HttpMessageNotReadableException.class
  })
  public ResponseEntity<Result<Void>> handleBadRequest(Exception exception) {
    return ResponseEntity.badRequest().body(Result.fail(ResultCode.PARAM_NOT_VALID));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
    LOGGER.error("Unexpected Yak Security request failure", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Result.fail(ResultCode.COMMON_FAIL));
  }

  private HttpStatus resolveStatus(ErrorCode errorCode) {
    if (!(errorCode instanceof ResultCode code)) {
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
    return switch (code) {
      case PARAM_NOT_VALID, PARAM_IS_BLANK, PARAM_ID_IS_BLANK, PARAM_TYPE_ERROR,
          PARAM_NOT_COMPLETE, PARAM_LENGTH_ERROR, PARAM_ERROR, USER_EMAIL_FORMAT_ERROR,
          USER_ID_CANNOT_BE_NULL, USER_PHONE_FORMAT_ERROR, USER_NAME_FORMAT_ERROR ->
          HttpStatus.BAD_REQUEST;
      case USER_NOT_LOGIN, USER_ACCOUNT_EXPIRED, USER_CREDENTIALS_ERROR,
          USER_CREDENTIALS_EXPIRED, USER_ACCOUNT_DISABLE, USER_ACCOUNT_LOCKED,
          USER_ACCOUNT_USE_BY_OTHERS -> HttpStatus.UNAUTHORIZED;
      case USER_ACCOUNT_NOT_EXIST, USER_NOT_EXISTS -> HttpStatus.NOT_FOUND;
      case USER_ACCOUNT_ALREADY_EXIST, USER_PHONE_EXIST, USER_EMAIL_EXIST,
          USER_NAME_EXISTS -> HttpStatus.CONFLICT;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
