package io.yak.ops.plugin.datasource.api.plugin;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;

/**
 * 数据源插件稳定扩展契约，负责插件描述、连接参数、连通性和 Catalog 元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourcePlugin {

    /** 插件稳定类型标识，例如 MYSQL、POSTGRE_SQL。 */
    String type();

    /** @return Provider 的稳定运行时元数据 */
    DataSourcePluginDescriptor descriptor();

    /**
     * 解析并规范化连接参数。
     *
     * @param connectionJson 原始连接 JSON
     * @return Provider 可直接使用的规范化连接对象
     */
    DataSourceConnection parseConnection(String connectionJson);

    /**
     * 执行一次连接可用性测试。
     *
     * @param connection 已解析的连接参数
     * @param timeoutSeconds 连接超时时间，单位秒
     */
    void testConnection(DataSourceConnection connection, int timeoutSeconds);

    /**
     * 创建 Catalog 元数据访问入口。
     *
     * @param connection 已解析的连接参数
     * @param timeoutSeconds 连接与查询的默认超时时间，单位秒
     */
    DataSourceCatalog createCatalog(DataSourceConnection connection, int timeoutSeconds);

    /** 判断 Provider 是否显式声明指定能力。 */
    default boolean supports(DataSourceCapability capability) {
        DataSourcePluginDescriptor value = descriptor();
        return value != null && value.supports(capability);
    }

    /** 判断 JDBC URL 是否属于当前 Provider；非 JDBC Provider 可保持默认 false。 */
    default boolean acceptsUrl(String jdbcUrl) {
        return false;
    }
}
