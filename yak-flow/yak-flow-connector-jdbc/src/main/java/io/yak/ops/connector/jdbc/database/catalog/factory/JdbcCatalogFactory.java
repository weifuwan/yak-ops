package io.yak.ops.connector.jdbc.database.catalog.factory;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.DriverManagerJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import java.util.Objects;

/**
 * Constructs read-only JDBC Catalog instances using the Connector's vendor factory SPI.
 *
 * <p>Catalog methods return YakFlow {@code TableSchema} and {@code TableId} contracts,
 * without a Flink Table API dependency or a duplicate Datasource Catalog implementation.
 * Vendor lookup is shared with JDBC Source/Sink dialect resolution.
 */
public final class JdbcCatalogFactory {

    private JdbcCatalogFactory() {}

    /**
     * Creates a Catalog backed by fresh DriverManager metadata connections.
     *
     * @param options database URL, driver and credentials for the provider
     * @return the matching vendor read-only Catalog
     */
    public static JdbcCatalog create(JdbcConnectionOptions options) {
        Objects.requireNonNull(options, "options");
        return create(options.url(), new DriverManagerJdbcConnectionProvider(options));
    }

    /**
     * Uses an injected connection provider, such as an isolated or tunneled driver runtime.
     *
     * @param jdbcUrl vendor-discovery URL
     * @param connections capability to open independent metadata connections
     * @return a vendor-specific, read-only Catalog
     */
    public static JdbcCatalog create(String jdbcUrl, JdbcConnectionProvider connections) {
        return JdbcFactoryLoader.loadCatalog(jdbcUrl, Objects.requireNonNull(connections, "connections"));
    }
}
