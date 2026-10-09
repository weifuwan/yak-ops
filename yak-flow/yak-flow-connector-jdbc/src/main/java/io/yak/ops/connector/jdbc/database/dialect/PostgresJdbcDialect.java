package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.TableId;
import java.sql.Connection;
import java.sql.SQLException;

/** PostgreSQL schema qualification and server-side cursor connection policy. */
public final class PostgresJdbcDialect implements JdbcDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + JdbcDialect.requireIdentifier(identifier).replace("\"", "\"\"") + "\"";
    }

    @Override
    public String qualifiedTable(TableId tableId) {
        return tableId.schema() == null
                ? quoteIdentifier(tableId.table())
                : quoteIdentifier(tableId.schema()) + "." + quoteIdentifier(tableId.table());
    }

    @Override
    public void configureReadConnection(Connection connection) throws SQLException {
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
    }
}
