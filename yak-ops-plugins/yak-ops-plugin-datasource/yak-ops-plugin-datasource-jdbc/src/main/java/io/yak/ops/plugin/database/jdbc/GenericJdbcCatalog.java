package io.yak.ops.plugin.database.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * 基于 JDBC DatabaseMetaData 实现数据库、Schema、表和字段元数据发现。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public class GenericJdbcCatalog implements DataSourceCatalog {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_TABLE_SEARCH_LIMIT = 500;

    private final JdbcConnectionProperties connection;
    private final int connectionTimeoutSeconds;

    public GenericJdbcCatalog(JdbcConnectionProperties connection, int timeoutSeconds) {
        this.connection = connection;
        this.connectionTimeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public GenericJdbcCatalog(
            JdbcConnectionProperties connection, int connectionTimeoutSeconds, int ignoredMetadataTimeoutSeconds) {
        this(connection, connectionTimeoutSeconds);
    }

    @Override
    public List<String> listDatabases() {
        try (Connection opened = openConnection()) {
            Set<String> databases = new LinkedHashSet<>();
            try (ResultSet resultSet = opened.getMetaData().getCatalogs()) {
                while (resultSet.next()) {
                    String database = resultSet.getString(1);
                    if (includeDatabase(database)) databases.add(database);
                }
            }
            if (databases.isEmpty() && includeDatabase(connection.database())) databases.add(connection.database());
            return new ArrayList<>(databases);
        } catch (Exception exception) {
            throw catalogError("读取数据库列表失败", exception);
        }
    }

    @Override
    public List<String> listSchemas(String database) {
        try (Connection opened = openConnection()) {
            Set<String> schemas = new LinkedHashSet<>();
            DatabaseMetaData metadata = opened.getMetaData();
            String catalog = metadataCatalog(database);
            try (ResultSet resultSet = schemas(metadata, catalog)) {
                while (resultSet.next()) {
                    String schema = resultSet.getString("TABLE_SCHEM");
                    if (includeSchema(schema)) schemas.add(schema);
                }
            }
            if (schemas.isEmpty() && includeSchema(connection.schema())) schemas.add(connection.schema());
            return new ArrayList<>(schemas);
        } catch (Exception exception) {
            throw catalogError("读取 Schema 列表失败", exception);
        }
    }

    @Override
    public List<DataSourceTable> listTables(DataSourceCatalogQuery query) {
        DataSourceCatalogQuery value = query == null ? new DataSourceCatalogQuery(null, null, null) : query;
        String database = metadataCatalog(value.getDatabase());
        String schema = metadataSchema(value.getSchema(), value.getLimit() != null);
        String keyword = trimToNull(value.getKeyword());
        int limit = value.getLimit() == null
                ? Integer.MAX_VALUE
                : Math.min(MAX_TABLE_SEARCH_LIMIT, Math.max(1, value.getLimit()));

        try (Connection opened = openConnection()) {
            DatabaseMetaData metadata = opened.getMetaData();
            String tableNamePattern = tableNamePattern(metadata, keyword);
            try (ResultSet resultSet = metadata.getTables(database, schema, tableNamePattern, tableTypes())) {
                List<DataSourceTable> tables = new ArrayList<>();
                while (resultSet.next() && tables.size() < limit) {
                    String name = resultSet.getString("TABLE_NAME");
                    if (!matchesKeyword(name, keyword)) continue;
                    tables.add(new DataSourceTable(
                            resultSet.getString("TABLE_CAT"),
                            resultSet.getString("TABLE_SCHEM"),
                            name,
                            resultSet.getString("TABLE_TYPE"),
                            resultSet.getString("REMARKS")));
                }
                return tables;
            }
        } catch (Exception exception) {
            throw catalogError("读取表列表失败", exception);
        }
    }

    @Override
    public List<DataSourceColumn> listColumns(DataSourceTablePath tablePath) {
        String database = metadataCatalog(tablePath.getDatabase());
        String schema = metadataSchema(tablePath.getSchema(), true);
        try (Connection opened = openConnection()) {
            DatabaseMetaData metadata = opened.getMetaData();
            Set<String> primaryKeys = primaryKeys(metadata, database, schema, tablePath.getTable());
            List<DataSourceColumn> columns = new ArrayList<>();
            try (ResultSet resultSet = metadata.getColumns(database, schema, tablePath.getTable(), "%")) {
                while (resultSet.next()) {
                    String name = resultSet.getString("COLUMN_NAME");
                    columns.add(new DataSourceColumn(
                            name,
                            resultSet.getString("TYPE_NAME"),
                            resultSet.getInt("DATA_TYPE"),
                            nullableInteger(resultSet, "COLUMN_SIZE"),
                            nullableInteger(resultSet, "DECIMAL_DIGITS"),
                            resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            resultSet.getInt("ORDINAL_POSITION"),
                            primaryKeys.contains(name),
                            resultSet.getString("REMARKS")));
                }
            }
            return columns;
        } catch (Exception exception) {
            throw catalogError("读取字段列表失败", exception);
        }
    }

    protected Connection openConnection() throws Exception {
        Class.forName(connection.driverClassName());
        DriverManager.setLoginTimeout(connectionTimeoutSeconds);
        return DriverManager.getConnection(connection.jdbcUrl(), connectionPropertiesInternal());
    }

    protected JdbcConnectionProperties connectionProperties() {
        return connection;
    }

    protected boolean includeDatabase(String database) {
        return !isBlank(database);
    }

    protected boolean includeSchema(String schema) {
        return !isBlank(schema);
    }

    protected String[] tableTypes() {
        return new String[] {"TABLE", "VIEW"};
    }

    protected boolean matchesKeyword(String value, String keyword) {
        return keyword == null
                || (value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)));
    }

    protected String quoteIdentifier(String identifier) {
        if (isBlank(identifier)) throw new IllegalArgumentException("数据库标识符不能为空");
        if ("SQL_SERVER".equals(connection.type())) {
            return "[" + identifier.trim().replace("]", "]]") + "]";
        }
        String quote = usesCatalogAsNamespace() ? "`" : "\"";
        return quote + identifier.trim().replace(quote, quote + quote) + quote;
    }

    protected DataSourcePluginException catalogError(String action, Throwable throwable) {
        String message = safeMessage(throwable);
        return new DataSourcePluginException(
                Operation.CATALOG, action + (message == null ? "" : "：" + message), throwable);
    }

    protected String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (isBlank(message)) return throwable == null ? null : throwable.getClass().getSimpleName();
        String sanitized = message.replaceAll("(?i)(password|pwd)=([^;&\\s]+)", "$1=******");
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private String metadataCatalog(String requestedDatabase) {
        if (usesOracleStyle() || "HANA".equals(connection.type())) return null;
        return firstNonBlank(requestedDatabase, connection.database());
    }

    private String metadataSchema(String requestedSchema, boolean narrowOracleDefault) {
        String schema = firstNonBlank(requestedSchema, connection.schema());
        if (!usesOracleStyle()) return schema;
        if (isBlank(schema) && narrowOracleDefault) schema = trimToNull(connection.username());
        return schema == null ? null : schema.toUpperCase(Locale.ROOT);
    }

    private String tableNamePattern(DatabaseMetaData metadata, String keyword) throws SQLException {
        if (keyword == null) return "%";
        String normalized = keyword;
        if (metadata.storesUpperCaseIdentifiers()) normalized = normalized.toUpperCase(Locale.ROOT);
        else if (metadata.storesLowerCaseIdentifiers()) normalized = normalized.toLowerCase(Locale.ROOT);

        String escape = trimToNull(metadata.getSearchStringEscape());
        if (escape != null) {
            normalized = normalized.replace(escape, escape + escape);
            normalized = normalized.replace("%", escape + "%");
            normalized = normalized.replace("_", escape + "_");
        }
        return "%" + normalized + "%";
    }

    private ResultSet schemas(DatabaseMetaData metadata, String database) throws SQLException {
        try {
            return metadata.getSchemas(database, null);
        } catch (SQLFeatureNotSupportedException | AbstractMethodError exception) {
            return metadata.getSchemas();
        }
    }

    private Set<String> primaryKeys(DatabaseMetaData metadata, String database, String schema, String table) {
        try (ResultSet resultSet = metadata.getPrimaryKeys(database, schema, table)) {
            Set<String> keys = new LinkedHashSet<>();
            while (resultSet.next()) keys.add(resultSet.getString("COLUMN_NAME"));
            return keys;
        } catch (Exception ignored) {
            return Collections.emptySet();
        }
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private Properties connectionPropertiesInternal() {
        Properties properties = new Properties();
        properties.putAll(connection.properties());
        if (!isBlank(connection.username())) properties.setProperty("user", connection.username());
        if (connection.password() != null) properties.setProperty("password", connection.password());
        return properties;
    }

    private boolean usesCatalogAsNamespace() {
        return connection.dbType() == DataSourceDbType.MYSQL
                || connection.dbType() == DataSourceDbType.TIDB
                || connection.dbType() == DataSourceDbType.GOLDENDB
                || connection.dbType() == DataSourceDbType.GBASE8A
                || connection.dbType() == DataSourceDbType.DORIS
                || connection.dbType() == DataSourceDbType.STARROCKS
                || connection.dbType() == DataSourceDbType.CLICKHOUSE
                || (connection.dbType() == DataSourceDbType.OCEANBASE && !oceanBaseOracleMode());
    }

    private boolean usesOracleStyle() {
        return connection.dbType() == DataSourceDbType.ORACLE
                || (connection.dbType() == DataSourceDbType.OCEANBASE && oceanBaseOracleMode());
    }

    private boolean oceanBaseOracleMode() {
        if (connection.dbType() != DataSourceDbType.OCEANBASE || isBlank(connection.normalizedJson())) return false;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(connection.normalizedJson());
            String mode = root.path("compatibleMode").asText(root.path("compatible_mode").asText("mysql"));
            return "oracle".equalsIgnoreCase(mode);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? trimToNull(fallback) : value.trim();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
