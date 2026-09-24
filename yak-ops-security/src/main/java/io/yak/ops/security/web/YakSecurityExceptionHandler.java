package io.yak.ops.security.web;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.exception.YakSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Yak Security Web 接口的统一异常与 HTTP 状态映射。
 */
@RestControllerAdvice(basePackages = "io.yak.ops.security.controller")
public class YakSecurityExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(YakSecurityExceptionHandler.class);

    /**
     * 处理安全模块业务异常。
     */
    @ExceptionHandler(YakSecurityException.class)
    public ResponseEntity<Result<Void>> handleYakSecurityException(
            YakSecurityException exception) {
        ErrorCode codeMsg = exception.getErrorCode();
        Result<Void> body = codeMsg == null
                ? Result.fail(exception)
                : Result.fail(codeMsg.getCode(), codeMsg.getMessage());
        HttpStatus status = resolveStatus(codeMsg);
        if (status.is5xxServerError()) {
            LOGGER.error("Yak Security request failed", exception);
        }
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 处理请求参数绑定、类型转换和请求体解析错误。
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(
                Result.fail(ResultCode.PARAM_NOT_VALID));
    }

    /**
     * 避免未处理异常以 HTTP 200 或泄露内部异常信息的形式返回。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(
            Exception exception) {
        LOGGER.error("Unexpected Yak Security request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.COMMON_FAIL));
    }

    private HttpStatus resolveStatus(ErrorCode codeMsg) {
        if (!(codeMsg instanceof ResultCode)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ResultCode code = (ResultCode) codeMsg;
        switch (code) {
            case PARAM_NOT_VALID:
            case PARAM_IS_BLANK:
            case PARAM_ID_IS_BLANK:
            case PARAM_TYPE_ERROR:
            case PARAM_NOT_COMPLETE:
            case PARAM_LENGTH_ERROR:
            case PARAM_ERROR:
            case USER_EMAIL_FORMAT_ERROR:
            case USER_ID_CANNOT_BE_NULL:
            case USER_PHONE_FORMAT_ERROR:
            case USER_NAME_FORMAT_ERROR:
            case ROLE_NAME_CANNOT_BE_BLANK:
            case ROLE_DEPT_CANNOT_BE_BLANK:
            case ROLE_PERMISSION_CANNOT_BE_NULL:
            case ROLE_ASSIGN_FLAG_IS_NULL:
            case ROLE_ID_CANNOT_BE_NULL:
            case PROJECT_ID_CANNOT_BE_NULL:
            case PROJECT_NAME_CANNOT_BE_BLANK:
            case PROJECT_DES_CANNOT_BE_BLANK:
            case PROJECT_DEPT_CANNOT_BE_NULL:
            case PROJECT_CHARGE_USER_CANNOT_BE_NULL:
            case RESOURCE_ASSIGN_ERROR:
            case RESOURCE_ASSIGN_ERROR_2:
            case RESOURCE_INVALID_SHOW_LEVEL:
            case RESOURCE_SHOW_LEVEL_ERROR:
            case RESOURCE_SHOW_LEVEL_ERROR_2:
            case RESOURCE_ASSIGN_BATCH_FLAG_CANNOT_BE_NULL:
            case RESOURCE_INVALID_CONTROL_LEVEL:
            case RESOURCE_TYPE_ID_CANNOT_BE_NULL:
            case RESOURCE_ID_CANNOT_BE_NULL:
                return HttpStatus.BAD_REQUEST;
            case USER_NOT_LOGIN:
            case USER_ACCOUNT_EXPIRED:
            case USER_CREDENTIALS_ERROR:
            case USER_CREDENTIALS_EXPIRED:
            case USER_ACCOUNT_DISABLE:
            case USER_ACCOUNT_LOCKED:
            case USER_ACCOUNT_USE_BY_OTHERS:
                return HttpStatus.UNAUTHORIZED;
            case NO_PERMISSION:
                return HttpStatus.FORBIDDEN;
            case USER_ACCOUNT_NOT_EXIST:
            case USER_NOT_EXISTS:
            case ROLE_NOT_EXISTS:
            case PROJECT_NOT_EXISTS:
            case OPLOG_NOT_EXIST:
            case MESSAGE_NOT_EXIST:
            case RESOURCE_TYPE_NOT_EXISTS:
                return HttpStatus.NOT_FOUND;
            case USER_ACCOUNT_ALREADY_EXIST:
            case USER_PHONE_EXIST:
            case USER_EMAIL_EXIST:
            case USER_NAME_EXISTS:
            case ROLE_USER_AUTHED:
            case ROLE_NAME_ALREADY_EXISTS:
            case PROJECT_NAME_ALREADY_EXISTS:
            case PROJECT_UN_RUNNING:
            case PROJECT_DEL_RESOURCE_NOT_NULL:
            case RESOURCE_DUPLICATION:
                return HttpStatus.CONFLICT;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
