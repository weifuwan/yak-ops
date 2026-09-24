package io.yak.ops.business.datasource.exception;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.security.SensitiveTextMasker;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 数据源管理接口异常转换。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "io.yak.ops.business.datasource.controller")
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceExceptionHandler {

  private final SensitiveTextMasker textMasker;

  @ExceptionHandler(YakSecurityException.class)
  public ResponseEntity<Result<Void>> handleSecurityException(
      YakSecurityException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    Result<Void> body =
        errorCode == null
            ? Result.fail(exception)
            : Result.fail(errorCode.getCode(), errorCode.getMessage());
    HttpStatus status =
        errorCode == ResultCode.NO_PERMISSION
            ? HttpStatus.FORBIDDEN
            : HttpStatus.UNAUTHORIZED;
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(DataSourceException.class)
  public Result<Void> handleDataSourceException(DataSourceException exception) {
    String message = textMasker.mask(exception.getUserMessage());
    if (exception.getErrorCode() == null) {
      return Result.fail(message);
    }
    return Result.fail(exception.getErrorCode().getCode(), message);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    BindException.class,
    HttpMessageNotReadableException.class
  })
  public Result<Void> handleInvalidRequest(Exception exception) {
    return Result.buildParamIllegal(resolveValidationMessage(exception));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public Result<Void> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    log.warn("Datasource persistence constraint violation", exception);
    return Result.fail(
        DataSourceErrorCode.DUPLICATE_NAME.getCode(),
        DataSourceErrorCode.DUPLICATE_NAME.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public Result<Void> handleUnexpectedException(Exception exception) {
    log.error("Unexpected datasource management error", exception);
    return Result.fail("数据源操作失败，请稍后重试");
  }

  private String resolveValidationMessage(Exception exception) {
    BindingResult bindingResult = null;
    if (exception instanceof MethodArgumentNotValidException validException) {
      bindingResult = validException.getBindingResult();
    } else if (exception instanceof BindException bindException) {
      bindingResult = bindException.getBindingResult();
    }
    if (bindingResult != null && bindingResult.getFieldError() != null) {
      return bindingResult.getFieldError().getDefaultMessage();
    }
    return "请求参数格式不正确";
  }
}
