package io.yak.ops.spi.datasource;

/**
 * 数据源插件可显式声明的稳定能力。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public enum DataSourceCapability {
    CONNECTION_TEST,
    CATALOG_METADATA,
    SSH_TUNNEL
}
