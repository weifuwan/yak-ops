package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.TableId;

/** MySQL quoted-identifier and catalog-qualified SQL rules. */
public final class MySqlJdbcDialect implements JdbcDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + JdbcDialect.requireIdentifier(identifier).replace("`", "``") + "`";
    }

    @Override
    public String qualifiedTable(TableId tableId) {
        return tableId.catalog() == null
                ? quoteIdentifier(tableId.table())
                : quoteIdentifier(tableId.catalog()) + "." + quoteIdentifier(tableId.table());
    }
}
