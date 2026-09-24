package io.yak.ops.spi.datasource;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;

/** Datasource plugin stable extension contract. */
public interface DataSourcePlugin {

    /** Plugin identity. */
    DataSourceDbType dbType();

    /** Immutable plugin metadata, declared capabilities and connection form schema. */
    DataSourcePluginDescriptor descriptor();

    /** Parse, validate and normalize connection JSON. */
    DataSourceConnection parseConnection(String connectionJson);

    /** Test connectivity; failures must throw {@link DataSourcePluginException}. */
    void testConnection(DataSourceConnection connection, int timeoutSeconds);

    /** Create the plugin-owned Catalog accessor. */
    DataSourceCatalog createCatalog(DataSourceConnection connection, int timeoutSeconds);

    /**
     * Create the plugin-owned Catalog accessor with independent connection and query timeouts.
     *
     * <p>Existing plugins keep source/binary behavior through the default bridge. Plugins that can
     * enforce statement-level timeout should override this method.
     */
    default DataSourceCatalog createCatalog(
            DataSourceConnection connection, int connectionTimeoutSeconds, int queryTimeoutSeconds) {
        return createCatalog(connection, connectionTimeoutSeconds);
    }

    /** Whether this plugin explicitly declares a stable capability. */
    default boolean supports(DataSourceCapability capability) {
        DataSourcePluginDescriptor value = descriptor();
        return value != null && value.supports(capability);
    }

    /** Create one physical SQL executor. Plugins without SQL capability keep the default failure. */
    default DataSourceSqlExecutor createSqlExecutor(DataSourceConnection connection, int connectionTimeoutSeconds) {
        throw new DataSourcePluginException(
                DataSourcePluginException.Operation.EXECUTION, "当前数据源插件不支持 SQL 执行：" + dbType());
    }

    /** Whether the plugin understands the supplied JDBC URL. */
    default boolean acceptsUrl(String jdbcUrl) {
        return false;
    }
}
