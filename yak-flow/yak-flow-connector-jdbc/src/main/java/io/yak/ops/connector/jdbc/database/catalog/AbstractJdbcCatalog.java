package io.yak.ops.connector.jdbc.database.catalog;

import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Metadata-driven JDBC Catalog implementation shared by all built-in database vendors.
 *
 * <p>Like Flink's AbstractJdbcCatalog, it centralizes table discovery, exact metadata
 * resolution and ordered primary-key extraction. Every operation owns its JDBC connection;
 * no connection, statement or result set is retained in checkpointable Catalog state.
 */
public abstract class AbstractJdbcCatalog implements JdbcCatalog {

    protected final JdbcConnectionProvider connections;
    protected final JdbcDialect dialect;

    protected AbstractJdbcCatalog(JdbcConnectionProvider connections, JdbcDialect dialect) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    @Override
    public List<String> listDatabases() throws SQLException {
        try (Connection connection = connections.getConnection()) {
            String database = connection.getCatalog();
            return database == null || database.isBlank() ? List.of() : List.of(database);
        }
    }

    @Override
    public List<String> listSchemas(String database) throws SQLException {
        try (Connection connection = connections.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = effectiveCatalog(connection, database);
            LinkedHashSet<String> names = new LinkedHashSet<>();
            try (ResultSet schemas = metadata.getSchemas(catalog, null)) {
                while (schemas.next()) {
                    String schema = schemas.getString("TABLE_SCHEM");
                    if (schema != null && !schema.isBlank() && includeSchema(schema)) {
                        names.add(schema);
                    }
                }
            }
            return List.copyOf(names);
        }
    }

    @Override
    public List<TableId> listTables(String database, String schema) throws SQLException {
        try (Connection connection = connections.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = effectiveCatalog(connection, database);
            String resolvedSchema = effectiveSchema(connection, schema);
            List<TableId> tables = new ArrayList<>();
            try (ResultSet result = metadata.getTables(catalog, resolvedSchema, "%", new String[] {"TABLE", "VIEW"})) {
                while (result.next()) {
                    String actualSchema = result.getString("TABLE_SCHEM");
                    if (!includeSchema(actualSchema)) {
                        continue;
                    }
                    tables.add(
                            new TableId(result.getString("TABLE_CAT"), actualSchema, result.getString("TABLE_NAME")));
                }
            }
            return List.copyOf(tables);
        }
    }

    @Override
    public boolean tableExists(TableId tableId) throws SQLException {
        Objects.requireNonNull(tableId, "tableId");
        try (Connection connection = connections.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = effectiveCatalog(connection, tableId.catalog());
            String schema = effectiveSchema(connection, tableId.schema());
            try (ResultSet tables = metadata.getTables(
                    catalog, schema, escapePattern(metadata, tableId.table()), new String[] {"TABLE", "VIEW"})) {
                while (tables.next()) {
                    if (tableId.table().equals(tables.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    @Override
    public TableSchema getTable(TableId tableId) throws SQLException {
        Objects.requireNonNull(tableId, "tableId");
        try (Connection connection = connections.getConnection()) {
            return JdbcTableMetadata.readTable(
                    connection,
                    dialect,
                    tableId,
                    effectiveCatalog(connection, tableId.catalog()),
                    effectiveSchema(connection, tableId.schema()));
        }
    }

    protected String effectiveCatalog(Connection connection, String requested) throws SQLException {
        return requested == null || requested.isBlank() ? connection.getCatalog() : requested;
    }

    protected String effectiveSchema(Connection connection, String requested) throws SQLException {
        return requested == null || requested.isBlank() ? connection.getSchema() : requested;
    }

    protected boolean includeSchema(String schema) {
        return true;
    }

    static String escapePattern(DatabaseMetaData metadata, String identifier) throws SQLException {
        String escape = metadata.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) {
            return identifier;
        }
        return identifier
                .replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }

    @Override
    public void close() {
        // Every metadata operation closes its own connection.
    }
}
