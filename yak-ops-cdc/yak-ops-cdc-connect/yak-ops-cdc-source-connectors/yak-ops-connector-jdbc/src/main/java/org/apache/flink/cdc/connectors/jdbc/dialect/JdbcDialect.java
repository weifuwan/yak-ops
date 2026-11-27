package org.apache.flink.cdc.connectors.jdbc.dialect;

import org.apache.flink.cdc.common.event.TableId;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

/** Database identifier and catalog rules shared by JDBC metadata and query planning. */
public final class JdbcDialect {

    private final String quoteString;
    private final boolean catalogBased;

    JdbcDialect(String quoteString, boolean catalogBased) {
        this.quoteString = quoteString == null ? "" : quoteString.trim();
        this.catalogBased = catalogBased;
    }

    public static JdbcDialect create(DatabaseMetaData metadata) throws SQLException {
        String product = metadata.getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
        boolean catalogBased =
                normalized.contains("mysql")
                        || normalized.contains("mariadb")
                        || normalized.contains("oceanbase")
                        || normalized.contains("clickhouse")
                        || normalized.contains("doris")
                        || normalized.contains("starrocks");
        return new JdbcDialect(metadata.getIdentifierQuoteString(), catalogBased);
    }

    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty() || quoteString.isEmpty()) {
            return identifier;
        }
        return quoteString + identifier.replace(quoteString, quoteString + quoteString) + quoteString;
    }

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
                        ? currentSchema
                        : tableId.getSchemaName();
        return schema == null || schema.isEmpty()
                ? table
                : quoteIdentifier(schema) + "." + table;
    }

    public String catalogForLookup(String namespace, String schemaName) {
        return catalogBased
                ? (schemaName == null || schemaName.isEmpty() ? namespace : schemaName)
                : namespace;
    }

    public String schemaForLookup(String namespace, String schemaName) {
        return catalogBased ? null : schemaName;
    }

    public TableId toTableId(String catalog, String schema, String table) {
        if (catalogBased) {
            String database = catalog == null || catalog.isEmpty() ? schema : catalog;
            return database == null || database.isEmpty()
                    ? TableId.tableId(table)
                    : TableId.tableId(database, table);
        }
        if (schema != null && !schema.isEmpty()) {
            return TableId.tableId(schema, table);
        }
        if (catalog != null && !catalog.isEmpty()) {
            return TableId.tableId(catalog, table);
        }
        return TableId.tableId(table);
    }

    public boolean isCatalogBased() {
        return catalogBased;
    }
}
