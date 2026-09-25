package io.yak.ops.boot.controller.exception;

import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.security.ResultCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.ErrorCode;
import io.yak.ops.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** All Controllers share this fallback HTTP exception boundary. */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode == null) {
            LOGGER.warn("Business exception without ErrorCode", exception);
            String message = exception.getMessage();
            Result<Void> body = message == null || message.isBlank()
                    ? Result.fail(CommonErrorCode.PARAM_NOT_VALID)
                    : Result.fail(CommonErrorCode.PARAM_NOT_VALID.getCode(), message);
            return ResponseEntity.badRequest().body(body);
        }

        HttpStatus status = businessStatus(errorCode);
        if (status.is5xxServerError()) LOGGER.error("Business request failed", exception);
        return ResponseEntity.status(status).body(Result.fail(errorCode));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<Result<Void>> handleInvalidRequest(Exception exception) {
        LOGGER.debug("Invalid request", exception);
        return ResponseEntity.badRequest().body(Result.fail(CommonErrorCode.PARAM_NOT_VALID));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.BAD_REQUEST : status;
        ErrorCode errorCode =
                switch (resolved) {
                    case NOT_FOUND -> CommonErrorCode.RESOURCE_NOT_EXISTS;
                    case CONFLICT -> CommonErrorCode.RESOURCE_DUPLICATION;
                    default ->
                        resolved.is4xxClientError() ? CommonErrorCode.PARAM_NOT_VALID : CommonErrorCode.COMMON_FAIL;
                };
        return ResponseEntity.status(resolved).body(Result.fail(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unhandled controller exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(CommonErrorCode.COMMON_FAIL));
    }

    private static HttpStatus businessStatus(ErrorCode errorCode) {
        if (errorCode == CommonErrorCode.PARAM_NOT_VALID) return HttpStatus.BAD_REQUEST;
        if (errorCode == CommonErrorCode.RESOURCE_NOT_EXISTS) return HttpStatus.NOT_FOUND;
        if (errorCode == CommonErrorCode.RESOURCE_DUPLICATION) return HttpStatus.CONFLICT;
        if (errorCode == CommonErrorCode.COMMON_FAIL) return HttpStatus.INTERNAL_SERVER_ERROR;

        if (errorCode instanceof ResultCode code) {
            return switch (code) {
                case PARAM_NOT_VALID,
                        PARAM_IS_BLANK,
                        PARAM_ID_IS_BLANK,
                        PARAM_TYPE_ERROR,
                        PARAM_NOT_COMPLETE,
                        PARAM_LENGTH_ERROR,
                        PARAM_ERROR,
                        USER_EMAIL_FORMAT_ERROR,
                        USER_ID_CANNOT_BE_NULL,
                        USER_PHONE_FORMAT_ERROR,
                        USER_NAME_FORMAT_ERROR -> HttpStatus.BAD_REQUEST;
                case USER_NOT_LOGIN,
                        USER_ACCOUNT_EXPIRED,
                        USER_CREDENTIALS_ERROR,
                        USER_CREDENTIALS_EXPIRED,
                        USER_ACCOUNT_LOCKED,
                        USER_ACCOUNT_USE_BY_OTHERS -> HttpStatus.UNAUTHORIZED;
                case USER_ACCOUNT_DISABLE, NO_PERMISSION -> HttpStatus.FORBIDDEN;
                case USER_ACCOUNT_NOT_EXIST, USER_NOT_EXISTS -> HttpStatus.NOT_FOUND;
                case USER_ACCOUNT_ALREADY_EXIST, USER_PHONE_EXIST, USER_EMAIL_EXIST, USER_NAME_EXISTS ->
                    HttpStatus.CONFLICT;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
