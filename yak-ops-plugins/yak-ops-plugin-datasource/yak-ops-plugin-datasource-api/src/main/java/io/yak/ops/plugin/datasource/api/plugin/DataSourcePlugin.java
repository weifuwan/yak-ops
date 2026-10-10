package io.yak.ops.plugin.datasource.api.plugin;

import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import java.sql.Connection;
import java.util.List;

/**
 * 数据源插件稳定扩展契约，负责连接配置、连通性和 JDBC 连接创建。
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
     * Opens one independent JDBC Connection through this provider's selected driver and SSH lifecycle.
     *
     * <p>Datasource connection ownership stays in the provider; only metadata queries run in the
     * YakFlow Connector Catalog. The caller must close the returned Connection.
     *
     * @param connection resolved provider connection settings
     * @param timeoutSeconds connection timeout in seconds
     * @return a fresh JDBC Connection owned by the caller
     * @throws Exception if authentication, driver loading, tunnelling or connection fails
     */
    Connection openConnection(DataSourceConnection connection, int timeoutSeconds) throws Exception;

    /**
     * 返回当前 Provider 可推荐给高级参数编辑器的连接属性名。
     *
     * <p>这是轻量属性名发现能力，不是前端表单 Schema 或严格白名单；未知属性是否允许仍由 Provider 的连接参数规则决定。</p>
     *
     * @return 稳定、去重后的连接属性名列表
     */
    default List<String> connectionPropertyKeys() {
        return List.of();
    }

    /**
     * 返回产品界面展示使用的 JDBC 地址。
     *
     * <p>默认保持规范化 JDBC URL 不变；Provider 可以把独立连接属性投影到展示地址，但不得因此改变运行时连接对象。
     *
     * @param connection 已解析的连接参数
     * @return 产品界面展示使用的 JDBC 地址
     */
    default String displayJdbcUrl(DataSourceConnection connection) {
        return connection.jdbcUrl();
    }

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
