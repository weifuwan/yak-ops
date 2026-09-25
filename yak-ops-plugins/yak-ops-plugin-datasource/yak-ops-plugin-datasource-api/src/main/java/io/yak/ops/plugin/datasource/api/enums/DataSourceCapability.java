package io.yak.ops.plugin.datasource.api.enums;

/**
 * 数据源插件可显式声明的稳定能力。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public enum DataSourceCapability {

    /** Provider 支持执行连接可用性测试。 */
    CONNECTION_TEST,

    /** Provider 支持发现数据库、Schema、表和字段元数据。 */
    CATALOG_METADATA,

    /** Provider 支持通过 SSH 本地端口转发建立数据库连接。 */
    SSH_TUNNEL
}
