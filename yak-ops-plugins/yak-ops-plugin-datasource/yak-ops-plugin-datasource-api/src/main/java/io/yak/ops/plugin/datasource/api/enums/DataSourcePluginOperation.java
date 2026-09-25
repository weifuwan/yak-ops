package io.yak.ops.plugin.datasource.api.enums;

/**
 * 数据源插件失败发生的生命周期阶段。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public enum DataSourcePluginOperation {

    /** 连接参数解析、校验或规范化失败。 */
    PARAMETER,

    /** 驱动加载、网络连接或认证失败。 */
    CONNECTIVITY,

    /** Catalog 元数据访问失败。 */
    CATALOG
}
