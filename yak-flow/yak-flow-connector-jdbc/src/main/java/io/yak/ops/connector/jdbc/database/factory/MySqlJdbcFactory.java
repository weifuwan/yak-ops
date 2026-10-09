package io.yak.ops.connector.jdbc.database.factory;

import io.yak.ops.connector.jdbc.database.JdbcFactory;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.MySqlJdbcDialect;

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
}
