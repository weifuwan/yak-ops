package io.yak.ops.connector.jdbc.database.internal.catalog;

import io.yak.ops.connector.jdbc.database.catalog.AbstractJdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Oracle identifies tables by owner/schema rather than JDBC catalog/database.
 *
 * <p>Only the schema namespace is used for metadata queries. The current schema is
 * shown as the default database equivalent for clients needing a single selection.
 */
public final class OracleCatalog extends AbstractJdbcCatalog {

    public OracleCatalog(JdbcConnectionProvider connections, JdbcDialect dialect) {
        super(connections, dialect);
    }

    @Override
    public List<String> listDatabases() throws SQLException {
        try (Connection connection = connections.getConnection()) {
            String schema = connection.getSchema();
            return schema == null || schema.isBlank() ? List.of() : List.of(schema);
        }
    }

    @Override
    protected String effectiveCatalog(Connection connection, String requested) {
        return null;
    }

    @Override
    protected String effectiveSchema(Connection connection, String requested) throws SQLException {
        return requested == null || requested.isBlank() ? connection.getSchema() : requested;
    }

    @Override
    protected boolean includeSchema(String schema) {
        if (schema == null) {
            return false;
        }
        String normalized = schema.toUpperCase(Locale.ROOT);
        return !normalized.equals("SYS")
                && !normalized.equals("SYSTEM")
                && !normalized.equals("XDB")
                && !normalized.equals("OUTLN")
                && !normalized.equals("DBSNMP");
    }
}
