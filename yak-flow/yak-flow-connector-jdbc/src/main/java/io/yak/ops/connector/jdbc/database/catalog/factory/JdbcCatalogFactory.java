package io.yak.ops.connector.jdbc.database.catalog.factory;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.DriverManagerJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import java.util.Objects;

/**
 * Public Catalog entry point that delegates vendor selection to the same SPI as the JDBC Source.
 *
 * <p>Unlike Flink's Table API CatalogFactory, this creates a Core-type Catalog from a JDBC
 * provider without introducing Flink dependencies or touching the existing Datasource Catalog.
 */
public final class JdbcCatalogFactory {

    private JdbcCatalogFactory() {}

    public static JdbcCatalog create(JdbcConnectionOptions options) {
        Objects.requireNonNull(options, "options");
        return create(options.url(), new DriverManagerJdbcConnectionProvider(options));
    }

    public static JdbcCatalog create(String jdbcUrl, JdbcConnectionProvider connections) {
        return JdbcFactoryLoader.loadCatalog(jdbcUrl, Objects.requireNonNull(connections, "connections"));
    }
}
