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
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        return listTableInfos(database, schema, null, null).stream().map(JdbcTableInfo::tableId).toList();
    }

    @Override
    public List<JdbcTableInfo> listTableInfos(String database, String schema, String keyword, Integer limit)
            throws SQLException {
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("JDBC table search limit must be positive");
        }
        int maxResults = limit == null ? Integer.MAX_VALUE : Math.min(500, limit);
        String needle = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        try (Connection connection = connections.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = effectiveCatalog(connection, database);
            String resolvedSchema = effectiveSchema(connection, schema);
            List<JdbcTableInfo> tables = new ArrayList<>();
            try (ResultSet result = metadata.getTables(catalog, resolvedSchema, "%", new String[] {"TABLE", "VIEW"})) {
                while (result.next() && tables.size() < maxResults) {
                    String name = result.getString("TABLE_NAME");
                    String actualSchema = result.getString("TABLE_SCHEM");
                    if (name == null
                            || !includeSchema(actualSchema)
                            || (needle != null && !name.toLowerCase(Locale.ROOT).contains(needle))) {
                        continue;
                    }
                    tables.add(new JdbcTableInfo(
                            new TableId(result.getString("TABLE_CAT"), actualSchema, name),
                            result.getString("TABLE_TYPE"),
                            result.getString("REMARKS")));
                }
            }
            return List.copyOf(tables);
        }
    }

    @Override
    public Optional<JdbcTableInfo> findTable(TableId tableId) throws SQLException {
        Objects.requireNonNull(tableId, "tableId");
        try (Connection connection = connections.getConnection()) {
            return JdbcTableMetadata.findTableInfo(
                    connection,
                    tableId,
                    effectiveCatalog(connection, tableId.catalog()),
                    effectiveSchema(connection, tableId.schema()));
        }
    }

    @Override
    public boolean tableExists(TableId tableId) throws SQLException {
        return findTable(tableId).isPresent();
    }

    @Override
    public List<JdbcColumnInfo> getColumns(TableId tableId) throws SQLException {
        Objects.requireNonNull(tableId, "tableId");
        try (Connection connection = connections.getConnection()) {
            return JdbcTableMetadata.readColumns(
                    connection,
                    tableId,
                    effectiveCatalog(connection, tableId.catalog()),
                    effectiveSchema(connection, tableId.schema()));
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
