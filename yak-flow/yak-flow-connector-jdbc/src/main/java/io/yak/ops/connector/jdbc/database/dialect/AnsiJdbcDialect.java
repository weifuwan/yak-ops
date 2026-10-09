package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.TableId;

/** Standard SQL quoting for the embedded H2 integration-test backend. */
public final class AnsiJdbcDialect implements JdbcDialect {

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
}
