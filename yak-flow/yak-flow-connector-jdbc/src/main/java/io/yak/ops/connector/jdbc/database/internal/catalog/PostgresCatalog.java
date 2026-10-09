package io.yak.ops.connector.jdbc.database.internal.catalog;

import io.yak.ops.connector.jdbc.database.catalog.AbstractJdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * PostgreSQL Catalog exposes the current connected database and its accessible schemas.
 *
 * <p>Unlike a Flink Table SQL Catalog that constructs a different JDBC URL per database,
 * this Catalog never opens a different database through an existing ConnectionProvider.
 */
public final class PostgresCatalog extends AbstractJdbcCatalog {

    public PostgresCatalog(JdbcConnectionProvider connections, JdbcDialect dialect) {
        super(connections, dialect);
    }

    @Override
    protected String effectiveCatalog(Connection connection, String requested) throws SQLException {
        String current = connection.getCatalog();
        if (requested != null && current != null && !requested.equals(current)) {
            throw new SQLException("PostgreSQL Catalog cannot access a different database through this connection");
        }
        return current;
    }

    @Override
    protected boolean includeSchema(String schema) {
        return schema != null
                && !schema.equalsIgnoreCase("information_schema")
                && !schema.toLowerCase(Locale.ROOT).startsWith("pg_");
    }
}
