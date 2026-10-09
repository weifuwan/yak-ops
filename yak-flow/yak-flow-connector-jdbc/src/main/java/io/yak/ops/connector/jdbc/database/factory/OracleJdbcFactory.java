package io.yak.ops.connector.jdbc.database.factory;

import io.yak.ops.connector.jdbc.database.JdbcFactory;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.OracleJdbcDialect;

/** Oracle JDBC dialect registration for ServiceLoader discovery. */
public final class OracleJdbcFactory implements JdbcFactory {

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:oracle:");
    }

    @Override
    public JdbcDialect createDialect() {
        return new OracleJdbcDialect();
    }
}
