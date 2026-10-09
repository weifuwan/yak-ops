package io.yak.ops.connector.jdbc.database.internal;

import io.yak.ops.connector.jdbc.database.JdbcFactory;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.catalog.OracleCatalog;
import io.yak.ops.connector.jdbc.database.internal.dialect.OracleJdbcDialect;

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

    @Override
    public JdbcCatalog createCatalog(JdbcConnectionProvider connections) {
        return new OracleCatalog(connections, createDialect());
    }
}
