package io.yak.ops.plugin.datasource.api.exception;

import io.yak.ops.plugin.datasource.api.enums.DataSourcePluginOperation;

/**
 * 数据源插件参数、连接或 Catalog 元数据访问异常。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public class DataSourcePluginException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 发生失败的 Plugin 生命周期阶段。 */
    private final DataSourcePluginOperation operation;

    public DataSourcePluginException(DataSourcePluginOperation operation, String message) {
        super(message);
        this.operation = operation;
    }

    public DataSourcePluginException(DataSourcePluginOperation operation, String message, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }

    /** @return 发生失败的 Plugin 生命周期阶段 */
    public DataSourcePluginOperation getOperation() {
        return operation;
    }
}
