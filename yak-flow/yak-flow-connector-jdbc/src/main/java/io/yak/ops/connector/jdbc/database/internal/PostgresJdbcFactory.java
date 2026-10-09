package io.yak.ops.connector.jdbc.database.internal;

import io.yak.ops.connector.jdbc.database.JdbcFactory;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.internal.catalog.PostgresCatalog;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect;

/** PostgreSQL JDBC dialect registration for ServiceLoader discovery. */
public final class PostgresJdbcFactory implements JdbcFactory {

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    @Override
    public JdbcDialect createDialect() {
        return new PostgresJdbcDialect();

    }

    @Override
    public JdbcCatalog createCatalog(JdbcConnectionProvider connections) {
        return new PostgresCatalog(connections, createDialect());
    }
}
