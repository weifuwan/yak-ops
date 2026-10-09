package io.yak.ops.connector.jdbc.database;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;

/**
 * JDBC vendor provider discovered through Java ServiceLoader.
 *
 * <p>A factory creates stateless SQL dialect implementations. Database Catalog discovery
 * belongs to the Datasource boundary, not to a duplicate Connector-side Catalog factory.
 */
public interface JdbcFactory {

    boolean acceptsURL(String url);

    JdbcDialect createDialect();
}
