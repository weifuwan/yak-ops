package io.yak.ops.boot.controller.datasource;

import io.yak.ops.boot.controller.datasource.v1.DataSourceController;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.result.ErrorCode;
import io.yak.ops.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 Datasource 业务异常转换为统一 HTTP Result。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = DataSourceController.class)
public class DataSourceExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceExceptionHandler.class);

    @ExceptionHandler(DataSourceException.class)
    public Result<Void> handleDataSourceException(DataSourceException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode == null) {
            return Result.fail(exception.getUserMessage());
        }
        if (isInternalFailure(errorCode)) {
            LOG.error("数据源请求处理失败，errorCode={}", errorCode.getCode(), exception);
        } else if (errorCode == DataSourceErrorCode.PLUGIN_NOT_FOUND) {
            LOG.warn("数据源插件不可用，errorCode={}", errorCode.getCode());
        }
        return Result.fail(errorCode.getCode(), exception.getUserMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return Result.fail(
                DataSourceErrorCode.DUPLICATE_NAME.getCode(), DataSourceErrorCode.DUPLICATE_NAME.getMessage());
    }

    private static boolean isInternalFailure(ErrorCode errorCode) {
        return errorCode == DataSourceErrorCode.CREATE_FAILED
                || errorCode == DataSourceErrorCode.UPDATE_FAILED
                || errorCode == DataSourceErrorCode.DELETE_FAILED
                || errorCode == DataSourceErrorCode.QUERY_FAILED;
    }
}
