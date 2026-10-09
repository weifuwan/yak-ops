package io.yak.ops.connector.jdbc.database.internal;

import io.yak.ops.connector.jdbc.database.JdbcFactory;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.internal.catalog.AnsiCatalog;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;

/** ANSI SQL dialect for H2 integration tests and embedded JDBC acceptance. */
public final class AnsiJdbcFactory implements JdbcFactory {

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:h2:");
    }

    @Override
    public JdbcDialect createDialect() {
        return new AnsiJdbcDialect();

    }

    @Override
    public JdbcCatalog createCatalog(JdbcConnectionProvider connections) {
        return new AnsiCatalog(connections, createDialect());
    }
}
