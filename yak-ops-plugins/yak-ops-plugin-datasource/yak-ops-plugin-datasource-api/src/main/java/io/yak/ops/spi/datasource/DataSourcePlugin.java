package io.yak.ops.spi.datasource;

/**
 * 数据源插件稳定扩展契约，负责插件描述、连接参数、连通性和 Catalog 元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourcePlugin {

    /** 插件稳定类型标识，例如 MYSQL、POSTGRE_SQL。 */
    String type();

    DataSourcePluginDescriptor descriptor();

    DataSourceConnection parseConnection(String connectionJson);

    void testConnection(DataSourceConnection connection, int timeoutSeconds);

    DataSourceCatalog createCatalog(DataSourceConnection connection, int timeoutSeconds);

    default boolean supports(DataSourceCapability capability) {
        DataSourcePluginDescriptor value = descriptor();
        return value != null && value.supports(capability);
    }

    default boolean acceptsUrl(String jdbcUrl) {
        return false;
    }
}
