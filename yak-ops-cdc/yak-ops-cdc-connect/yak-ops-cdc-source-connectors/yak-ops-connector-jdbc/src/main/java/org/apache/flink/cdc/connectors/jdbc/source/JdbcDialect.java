package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.cdc.common.event.TableId;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

/** Identifier and catalog rules for JDBC databases. */
interface JdbcDialect {

    String quoteIdentifier(String identifier);
    String qualifiedTable(TableId tableId, String currentCatalog, String currentSchema);
    String catalogForLookup(String namespace, String schemaName);
    String schemaForLookup(String namespace, String schemaName);
    TableId toTableId(String catalog, String schema, String table);
    boolean isCatalogBased();

    static JdbcDialect create(DatabaseMetaData metadata) throws SQLException {
        String product = metadata.getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
        boolean catalogBased =
                normalized.contains("mysql")
                        || normalized.contains("mariadb")
                        || normalized.contains("oceanbase")
                        || normalized.contains("clickhouse")
                        || normalized.contains("doris")
                        || normalized.contains("starrocks");
        return new GenericJdbcDialect(metadata.getIdentifierQuoteString(), catalogBased);
    }
}

final class GenericJdbcDialect implements JdbcDialect {

    private final String quoteString;
    private final boolean catalogBased;

    GenericJdbcDialect(String quoteString, boolean catalogBased) {
        this.quoteString = quoteString == null ? "" : quoteString.trim();
        this.catalogBased = catalogBased;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty() || quoteString.isEmpty()) {
            return identifier;
        }
        return quoteString + identifier.replace(quoteString, quoteString + quoteString) + quoteString;
    }

    @Override
    public String qualifiedTable(TableId tableId, String currentCatalog, String currentSchema) {
        String table = quoteIdentifier(tableId.getTableName());
        if (catalogBased) {
            String catalog =
                    tableId.getNamespace() != null
                            ? tableId.getNamespace()
                            : tableId.getSchemaName() != null
                                    ? tableId.getSchemaName()
                                    : currentCatalog;
            return catalog == null || catalog.isEmpty()
                    ? table
                    : quoteIdentifier(catalog) + "." + table;
        }
        if (tableId.getNamespace() != null && tableId.getSchemaName() != null) {
            return quoteIdentifier(tableId.getNamespace())
                    + "." + quoteIdentifier(tableId.getSchemaName()) + "." + table;
        }
        String schema =
                tableId.getSchemaName() == null || tableId.getSchemaName().isEmpty()
                        ? currentSchema : tableId.getSchemaName();
        return schema == null || schema.isEmpty() ? table : quoteIdentifier(schema) + "." + table;
    }

    @Override
    public String catalogForLookup(String namespace, String schemaName) {
        return catalogBased
                ? (schemaName == null || schemaName.isEmpty() ? namespace : schemaName)
                : namespace;
    }

    @Override
    public String schemaForLookup(String namespace, String schemaName) {
        return catalogBased ? null : schemaName;
    }

    @Override
    public TableId toTableId(String catalog, String schema, String table) {
        if (catalogBased) {
            return TableId.tableId(catalog == null || catalog.isEmpty() ? schema : catalog, table);
        }
        if (schema != null && !schema.isEmpty()) {
            return TableId.tableId(schema, table);
        }
        if (catalog != null && !catalog.isEmpty()) {
            return TableId.tableId(catalog, table);
        }
        return TableId.tableId(table);
    }

    @Override
    public boolean isCatalogBased() { return catalogBased; }
}
