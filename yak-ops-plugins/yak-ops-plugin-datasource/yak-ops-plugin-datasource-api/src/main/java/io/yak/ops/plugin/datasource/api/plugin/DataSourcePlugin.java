package io.yak.ops.plugin.datasource.api.plugin;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import java.sql.Connection;
import java.util.List;

/**
 * Defines the datasource plugin SPI for connection settings, connectivity and JDBC connection creation.
 */
public interface DataSourcePlugin {

    /** Returns the stable provider type, such as MYSQL or POSTGRE_SQL. */
    String type();

    /** @return stable runtime metadata declared by this provider */
    DataSourcePluginDescriptor descriptor();

    /**
     * Declares the accepted connection fields and their validation rules.
     *
     * @return complete provider option rules
     */
    OptionRule optionRule();

    /**
     * Resolves a validated, immutable configuration to a normalized connection.
     *
     * @param configuration parsed connection values without credentials in diagnostics
     * @return runtime connection settings owned by the provider
     */
    DataSourceConnection parseConnection(ReadonlyConfig configuration);

    /**
     * Performs one connection availability test.
     *
     * @param connection normalized connection parameters
     * @param timeoutSeconds connection timeout, in seconds
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
     * Prepares connection settings for Catalog and Connector creation without opening a connection.
     *
     * @param connection parsed provider connection
     * @param timeoutSeconds connection timeout in seconds
     * @return immutable execution inputs; never persist them in task definitions or diagnostics
     */
    ReadonlyConfig connectionOptions(DataSourceConnection connection, int timeoutSeconds);

    /**
     * Lists provider-recommended connection properties for the advanced editor.
     *
     * <p>The list is discovery guidance, not a form schema or strict allowlist; the provider still validates unknown properties.
     *
     * @return stable, distinct property names
     */
    default List<String> connectionPropertyKeys() {
        return List.of();
    }

    /**
     * Returns a JDBC URL suitable for product display.
     *
     * <p>The default is the normalized URL; a provider may include displayable advanced settings without modifying the runtime connection.
     *
     * @param connection normalized connection parameters
     * @return display-only JDBC URL
     */
    default String displayJdbcUrl(DataSourceConnection connection) {
        return connection.jdbcUrl();
    }

    /** Checks whether this provider explicitly advertises a capability. */
    default boolean supports(DataSourceCapability capability) {
        DataSourcePluginDescriptor value = descriptor();
        return value != null && value.supports(capability);
    }

    /** Checks whether a JDBC URL belongs to this provider; non-JDBC plugins may return false. */
    default boolean acceptsUrl(String jdbcUrl) {
        return false;
    }
}
