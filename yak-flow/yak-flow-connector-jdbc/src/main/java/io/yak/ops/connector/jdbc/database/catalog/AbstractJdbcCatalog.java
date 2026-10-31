package io.yak.ops.connector.jdbc.database.catalog;

import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.query.JdbcReadQuery;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Metadata-driven JDBC Catalog implementation shared by all built-in database vendors.
 *
 * <p>Centralizes table discovery, exact metadata resolution and ordered primary-key
 * extraction. Every metadata operation owns its JDBC connection; checkpointable Catalog
 * state contains no live connection, statement or result set.
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
        return listTableInfos(database, schema, null, null).stream()
                .map(JdbcTableInfo::tableId)
                .toList();
    }

    @Override
    public List<JdbcTableInfo> listTableInfos(String database, String schema, String keyword, Integer limit)
            throws SQLException {
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("JDBC table search limit must be positive");
        }
        int maxResults = limit == null ? Integer.MAX_VALUE : Math.min(500, limit);
        String needle =
                keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
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
    public JdbcTablePage listTablePage(
            String database,
            String schema,
            String keyword,
            String databasePattern,
            String tablePattern,
            int offset,
            int limit)
            throws SQLException {
        if (offset < 0 || offset > 1000000 || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Invalid JDBC metadata page range");
        }
        Pattern databaseMatcher = metadataPattern(databasePattern);
        Pattern tableMatcher = metadataPattern(tablePattern);
        List<String> databases = database == null || database.isBlank() ? listDatabases() : List.of(database);
        if (databases.isEmpty()) databases = Collections.singletonList(null);
        List<JdbcTableInfo> matching = new ArrayList<>();
        for (String candidate : databases) {
            for (JdbcTableInfo table : listTableInfos(candidate, schema, keyword, null)) {
                TableId id = table.tableId();
                String namespace = id.catalog() != null ? id.catalog() : id.schema();
                if ((databaseMatcher == null
                                || databaseMatcher
                                        .matcher(namespace == null ? "" : namespace)
                                        .matches())
                        && (tableMatcher == null
                                || tableMatcher.matcher(id.table()).matches())) {
                    matching.add(table);
                }
            }
        }
        Comparator<String> names = Comparator.nullsFirst(Comparator.naturalOrder());
        matching.sort(
                Comparator.comparing((JdbcTableInfo table) -> table.tableId().catalog(), names)
                        .thenComparing(table -> table.tableId().schema(), names)
                        .thenComparing(table -> table.tableId().table()));
        int start = Math.min(offset, matching.size());
        int end = Math.min(start + limit, matching.size());
        return new JdbcTablePage(matching.subList(start, end), end < matching.size() ? end : null);
    }

    @Override
    public JdbcDataPreview preview(TableId tableId, String where, int limit) throws SQLException {
        Objects.requireNonNull(tableId, "tableId");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Preview row limit must be between 1 and 100");
        String predicate = JdbcReadQuery.validateWhere(where);
        List<JdbcColumnInfo> columns = getColumns(tableId);
        if (columns.isEmpty() || columns.size() > 256) {
            throw new IllegalArgumentException("Preview requires between 1 and 256 visible columns");
        }
        String projection = columns.stream()
                .map(column -> dialect.quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String sql = "SELECT " + projection + " FROM " + dialect.qualifiedTable(tableId)
                + JdbcReadQuery.whereClause(predicate, List.of());
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        int characters = 0;
        try (Connection connection = connections.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setMaxRows(limit + 1);
                statement.setQueryTimeout(10);
                statement.setFetchSize(Math.min(limit + 1, 100));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        if (rows.size() == limit) {
                            truncated = true;
                            break;
                        }
                        List<Object> row = new ArrayList<>();
                        for (int index = 0; index < columns.size(); index++) {
                            Object value = previewCell(
                                    result, index + 1, columns.get(index).jdbcType());
                            if (value instanceof String text) {
                                if (text.length() > 4096) {
                                    value = text.substring(0, 4096);
                                    truncated = true;
                                }
                                characters += ((String) value).length();
                            } else {
                                characters += 5;
                            }
                            row.add(value);
                        }
                        if (characters > 262144) {
                            truncated = true;
                            break;
                        }
                        rows.add(row);
                    }
                }
            }
        }
        return new JdbcDataPreview(columns, rows, truncated);
    }

    private static Pattern metadataPattern(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 256) throw new IllegalArgumentException("Metadata pattern is too long");
        return Pattern.compile(value);
    }

    private static Object previewCell(ResultSet result, int index, int type) throws SQLException {
        if (type == Types.BOOLEAN) {
            boolean value = result.getBoolean(index);
            return result.wasNull() ? null : value;
        }
        if (type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == Types.BLOB) {
            try (InputStream stream = result.getBinaryStream(index)) {
                return stream == null ? null : "0x" + HexFormat.of().formatHex(stream.readNBytes(2049));
            } catch (IOException failure) {
                throw new SQLException("Failed to read bounded binary preview", failure);
            }
        }
        if (type == Types.CHAR
                || type == Types.VARCHAR
                || type == Types.LONGVARCHAR
                || type == Types.NCHAR
                || type == Types.NVARCHAR
                || type == Types.LONGNVARCHAR
                || type == Types.CLOB
                || type == Types.NCLOB) {
            try (Reader reader = result.getCharacterStream(index)) {
                if (reader == null) return null;
                char[] buffer = new char[4097];
                int length = 0;
                while (length < buffer.length) {
                    int read = reader.read(buffer, length, buffer.length - length);
                    if (read < 0) break;
                    if (read == 0) continue;
                    length += read;
                }
                return new String(buffer, 0, length);
            } catch (IOException failure) {
                throw new SQLException("Failed to read bounded text preview", failure);
            }
        }
        // Numeric and temporal values remain textual; JavaScript must not round BIGINT/DECIMAL.
        return result.getString(index);
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
