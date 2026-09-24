package io.yak.ops.boot.controller.datasource;

import io.yak.ops.boot.controller.datasource.v1.DataSourceController;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourcePluginBusiness;
import io.yak.ops.common.Result;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 Datasource 业务异常转换为统一 HTTP Result，并在出站前再次遮罩敏感文本。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = DataSourceController.class)
@ConditionalOnDataSourceEnabled
public class DataSourceExceptionHandler {

    @Resource
    private DataSourcePluginBusiness pluginBusiness;

    @ExceptionHandler(DataSourceException.class)
    public Result<Void> handleDataSourceException(DataSourceException exception) {
        String message = pluginBusiness.maskSensitiveText(exception.getUserMessage());
        if (exception.getErrorCode() == null) {
            return Result.fail(message);
        }
        return Result.fail(exception.getErrorCode().getCode(), message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Datasource persistence constraint violation", exception);
        return Result.fail(
                DataSourceErrorCode.DUPLICATE_NAME.getCode(), DataSourceErrorCode.DUPLICATE_NAME.getMessage());
    }
}
