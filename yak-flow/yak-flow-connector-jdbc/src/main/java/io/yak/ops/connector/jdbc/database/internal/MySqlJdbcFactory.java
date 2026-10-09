package io.yak.ops.connector.jdbc.database.internal;

import io.yak.ops.connector.jdbc.database.JdbcFactory;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.internal.catalog.MySqlCatalog;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;

/** MySQL JDBC dialect registration for ServiceLoader discovery. */
public final class MySqlJdbcFactory implements JdbcFactory {

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:mysql:");
    }

    @Override
    public JdbcDialect createDialect() {
        return new MySqlJdbcDialect();

    }

    @Override
    public JdbcCatalog createCatalog(JdbcConnectionProvider connections) {
        return new MySqlCatalog(connections, createDialect());
    }
}
