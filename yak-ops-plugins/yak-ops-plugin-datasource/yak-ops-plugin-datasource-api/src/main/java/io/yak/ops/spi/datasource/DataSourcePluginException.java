package io.yak.ops.spi.datasource;

/**
 * 数据源插件参数、连接或 Catalog 元数据访问异常。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public class DataSourcePluginException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 发生失败的 Plugin 生命周期阶段。 */
    private final Operation operation;

    public DataSourcePluginException(Operation operation, String message) {
        super(message);
        this.operation = operation;
    }

    public DataSourcePluginException(Operation operation, String message, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }

    /** @return 发生失败的 Plugin 生命周期阶段 */
    public Operation getOperation() {
        return operation;
    }

    /**
     * 插件失败发生的阶段。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    public enum Operation {

        /** 连接参数解析、校验或规范化失败。 */
        PARAMETER,

        /** 驱动加载、网络连接或认证失败。 */
        CONNECTIVITY,

        /** Catalog 元数据访问失败。 */
        CATALOG
    }
}
