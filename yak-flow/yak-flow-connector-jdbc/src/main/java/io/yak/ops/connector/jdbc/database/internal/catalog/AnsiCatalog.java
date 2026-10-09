package io.yak.ops.connector.jdbc.database.internal.catalog;

import io.yak.ops.connector.jdbc.database.catalog.AbstractJdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;

/** ANSI/H2 Catalog used for embedded integration and metadata behavior tests. */
public final class AnsiCatalog extends AbstractJdbcCatalog {

    public AnsiCatalog(JdbcConnectionProvider connections, JdbcDialect dialect) {
        super(connections, dialect);
    }
}
