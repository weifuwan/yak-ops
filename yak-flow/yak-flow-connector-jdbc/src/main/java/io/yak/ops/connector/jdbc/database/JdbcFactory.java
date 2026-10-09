package io.yak.ops.connector.jdbc.database;

import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;

/**
 * JDBC vendor provider discovered through Java ServiceLoader.
 *
 * <p>A factory creates the vendor Dialect and Catalog in the JDBC Connector. The existing
 * Datasource Plugin Catalog is left unchanged until a separate product adapter migration.
 */
public interface JdbcFactory {

    boolean acceptsURL(String url);

    JdbcDialect createDialect();

    /** Returns a fully usable Catalog with its own JDBC connection ownership. */
    JdbcCatalog createCatalog(JdbcConnectionProvider connections);
}
