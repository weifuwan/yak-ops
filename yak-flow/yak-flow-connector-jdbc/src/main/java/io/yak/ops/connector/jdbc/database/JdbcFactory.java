package io.yak.ops.connector.jdbc.database;

import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;

/**
 * Discovers vendor-specific JDBC dialect and metadata catalog implementations through Java SPI.
 *
 * <p>Factories identify compatible JDBC URL prefixes without opening connections. The
 * returned dialect is a reusable SQL definition, whereas the Catalog uses the supplied
 * provider for independent caller-owned metadata connections. Datasource contributes
 * only its selected Driver and optional SSH ConnectionProvider; Catalog lives here.
 */
public interface JdbcFactory {

    /**
     * Tests whether this provider owns a JDBC URL without establishing a connection.
     *
     * @param url database JDBC URL, potentially containing credentials
     * @return true only when the provider recognizes its vendor's URL form
     */
    boolean acceptsURL(String url);

    /**
     * Creates a reusable, stateless database dialect for recognized URLs.
     *
     * @return vendor-specific SQL, quoting, and conversion rules
     */
    JdbcDialect createDialect();

    /**
     * Creates a metadata catalog backed by independent caller-owned JDBC connections.
     *
     * @param connections provider used for each catalog metadata operation
     * @return the vendor's read-only metadata catalog
     */
    JdbcCatalog createCatalog(JdbcConnectionProvider connections);
}
