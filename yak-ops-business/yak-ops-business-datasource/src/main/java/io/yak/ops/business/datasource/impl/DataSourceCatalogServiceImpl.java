package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourcePreviewDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePageDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePreviewVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceTablePageVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTablePath;
import jakarta.annotation.Resource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** JDBC metadata browser independent from the deleted data synchronization runtime. */
@Service
public class DataSourceCatalogServiceImpl implements DataSourceCatalogService {
    @Resource private DataSourceLookupService resources;

    @Override
    public List<String> listDatabases(String datasourceId) {
        return query(datasourceId, DataSourceErrorCode.CATALOG_QUERY_FAILED, connection -> {
            List<String> names = new ArrayList<>();
            try (ResultSet catalogs = connection.getMetaData().getCatalogs()) {
                while (catalogs.next()) names.add(catalogs.getString("TABLE_CAT"));
            }
            if (names.isEmpty()) {
                try (ResultSet schemas = connection.getMetaData().getSchemas()) {
                    while (schemas.next()) names.add(schemas.getString("TABLE_SCHEM"));
                }
            }
            return names;
        });
    }

    @Override
    public List<String> listSchemas(String datasourceId, String database) {
        return query(datasourceId, DataSourceErrorCode.CATALOG_QUERY_FAILED, connection -> {
            List<String> names = new ArrayList<>();
            try (ResultSet schemas = connection.getMetaData().getSchemas(StringUtils.trimToNull(database), null)) {
                while (schemas.next()) names.add(schemas.getString("TABLE_SCHEM"));
            }
            return names;
        });
    }

    @Override
    public List<DataSourceCatalogTableVO> listTables(String datasourceId, DataSourceCatalogQueryDTO parameters) {
        return query(datasourceId, DataSourceErrorCode.CATALOG_QUERY_FAILED, connection -> {
            List<DataSourceCatalogTableVO> tables = readTables(
                    connection, parameters.getDatabase(), parameters.getSchema(), parameters.getKeyword());
            int limit = boundedLimit(parameters.getLimit(), 500);
            return tables.subList(0, Math.min(limit, tables.size()));
        });
    }

    @Override
    public DataSourceTablePageVO listTablePage(String datasourceId, DataSourceTablePageDTO parameters) {
        final Pattern databasePattern;
        final Pattern tablePattern;
        try {
            databasePattern = compileOptional(parameters.getDatabasePattern());
            tablePattern = compileOptional(parameters.getTablePattern());
        } catch (IllegalArgumentException failure) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CATALOG_FILTER, null, failure);
        }
        DataSourceInfo info = resources.dataSource(WorkspaceContext.requireWorkspaceId(), datasourceId);
        DataSourceTablePath scope = info.resolveTable(parameters.getDatabase(), parameters.getSchema(), "_catalog_scope");
        return query(datasourceId, DataSourceErrorCode.CATALOG_QUERY_FAILED, connection -> {
            List<DataSourceCatalogTableVO> all = readTables(
                    connection, scope.catalog(), scope.schema(), parameters.getKeyword());
            List<DataSourceCatalogTableVO> matched = all.stream()
                    .filter(item -> databasePattern == null || databasePattern.matcher(
                            item.getDatabase() != null ? item.getDatabase() :
                                    item.getSchema() == null ? "" : item.getSchema()).matches())
                    .filter(item -> tablePattern == null || tablePattern.matcher(item.getName()).matches())
                    .toList();
            int start = Math.min(Math.max(0, parameters.getOffset()), matched.size());
            int end = Math.min(start + boundedLimit(parameters.getLimit(), 500), matched.size());
            return new DataSourceTablePageVO(matched.subList(start, end), end == matched.size() ? null : end);
        });
    }

    @Override
    public DataSourcePreviewVO preview(String datasourceId, DataSourcePreviewDTO parameters) {
        if (StringUtils.trimToNull(parameters.getWhere()) != null) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_PREVIEW_CONFIGURATION, null,
                    new UnsupportedOperationException("Filtered SQL previews are disabled while the engine is rebuilt"));
        }
        DataSourceInfo info = resources.dataSource(WorkspaceContext.requireWorkspaceId(), datasourceId);
        DataSourceTablePath path = info.resolveTable(
                parameters.getDatabase(), parameters.getSchema(), parameters.getTable());
        return query(datasourceId, DataSourceErrorCode.PREVIEW_QUERY_FAILED, connection -> {
            DatabaseMetaData meta = connection.getMetaData();
            String sql = "SELECT * FROM " + qualifiedName(meta, path);
            int limit = Math.min(100, Math.max(1, parameters.getLimit()));
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setMaxRows(limit + 1);
                statement.setQueryTimeout(30);
                try (ResultSet rs = statement.executeQuery()) {
                    ResultSetMetaData columns = rs.getMetaData();
                    List<DataSourceCatalogColumnVO> columnViews = new ArrayList<>();
                    for (int i = 1; i <= columns.getColumnCount(); i++) {
                        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
                        column.setName(columns.getColumnLabel(i));
                        column.setTypeName(columns.getColumnTypeName(i));
                        column.setJdbcType(columns.getColumnType(i));
                        column.setSize(columns.getPrecision(i));
                        column.setScale(columns.getScale(i));
                        column.setNullable(columns.isNullable(i) != ResultSetMetaData.columnNoNulls);
                        column.setOrdinalPosition(i);
                        columnViews.add(column);
                    }
                    List<List<Object>> rows = new ArrayList<>();
                    while (rows.size() <= limit && rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= columns.getColumnCount(); i++) {
                            Object cell = rs.getObject(i);
                            if (cell instanceof byte[] bytes) {
                                row.add(java.util.Base64.getEncoder().encodeToString(bytes));
                            } else if (cell instanceof Boolean || cell == null) {
                                row.add(cell);
                            } else {
                                row.add(cell.toString());
                            }
                        }
                        rows.add(row);
                    }
                    boolean truncated = rows.size() > limit;
                    if (truncated) rows.remove(rows.size() - 1);
                    return new DataSourcePreviewVO(columnViews, rows, truncated);
                }
            }
        });
    }

    @Override
    public List<DataSourceCatalogColumnVO> listColumns(String datasourceId, DataSourceTablePathDTO path) {
        DataSourceInfo info = resources.dataSource(WorkspaceContext.requireWorkspaceId(), datasourceId);
        DataSourceTablePath table = info.resolveTable(path.getDatabase(), path.getSchema(), path.getTable());
        return query(datasourceId, DataSourceErrorCode.CATALOG_QUERY_FAILED, connection -> {
            DatabaseMetaData meta = connection.getMetaData();
            Map<String, Integer> keyOrder = new HashMap<>();
            try (ResultSet keys = meta.getPrimaryKeys(table.catalog(), table.schema(), table.table())) {
                while (keys.next()) keyOrder.put(keys.getString("COLUMN_NAME"), keys.getInt("KEY_SEQ"));
            }
            List<DataSourceCatalogColumnVO> columns = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(table.catalog(), table.schema(), table.table(), null)) {
                while (rs.next()) {
                    DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
                    String name = rs.getString("COLUMN_NAME");
                    column.setName(name);
                    column.setTypeName(rs.getString("TYPE_NAME"));
                    column.setJdbcType(rs.getInt("DATA_TYPE"));
                    column.setSize(rs.getInt("COLUMN_SIZE"));
                    column.setScale(rs.getInt("DECIMAL_DIGITS"));
                    column.setNullable(rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                    column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                    column.setPrimaryKey(keyOrder.containsKey(name));
                    column.setPrimaryKeyPosition(keyOrder.get(name));
                    column.setRemarks(rs.getString("REMARKS"));
                    columns.add(column);
                }
            }
            return columns;
        });
    }

    private static List<DataSourceCatalogTableVO> readTables(
            Connection connection, String database, String schema, String keyword) throws SQLException {
        List<DataSourceCatalogTableVO> tables = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getTables(
                StringUtils.trimToNull(database), StringUtils.trimToNull(schema), "%",
                new String[] {"TABLE", "VIEW", "BASE TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (keyword != null && !name.toLowerCase(java.util.Locale.ROOT)
                        .contains(keyword.toLowerCase(java.util.Locale.ROOT))) continue;
                DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
                table.setDatabase(rs.getString("TABLE_CAT"));
                table.setSchema(rs.getString("TABLE_SCHEM"));
                table.setName(name);
                table.setType(rs.getString("TABLE_TYPE"));
                table.setRemarks(rs.getString("REMARKS"));
                tables.add(table);
            }
        }
        tables.sort(Comparator.comparing(DataSourceCatalogTableVO::getDatabase,
                        Comparator.nullsFirst(String::compareTo))
                .thenComparing(DataSourceCatalogTableVO::getSchema, Comparator.nullsFirst(String::compareTo))
                .thenComparing(DataSourceCatalogTableVO::getName));
        return tables;
    }

    private static String qualifiedName(DatabaseMetaData meta, DataSourceTablePath path) throws SQLException {
        String product = meta.getDatabaseProductName();
        String namespace = product != null && product.toLowerCase(java.util.Locale.ROOT).contains("mysql")
                ? path.catalog()
                : path.schema();
        return (namespace == null ? "" : quote(meta, namespace) + ".") + quote(meta, path.table());
    }

    private static String quote(DatabaseMetaData meta, String name) throws SQLException {
        if (name == null || name.isBlank() || name.length() > 128
                || name.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Invalid database identifier");
        }
        String mark = meta.getIdentifierQuoteString().trim();
        return mark.isEmpty() ? name : mark + name.replace(mark, mark + mark) + mark;
    }

    private static Pattern compileOptional(String pattern) {
        return StringUtils.trimToNull(pattern) == null ? null : Pattern.compile(pattern);
    }

    private static int boundedLimit(Integer value, int max) {
        return Math.min(max, Math.max(1, value == null ? 200 : value));
    }

    private <T> T query(String datasourceId, DataSourceErrorCode error, CatalogAction<T> action) {
        ReadonlyConfig options = resources.connectionOptions(WorkspaceContext.requireWorkspaceId(), datasourceId);
        Map<String, Object> settings = options.getSourceMap();
        if (Boolean.TRUE.equals(settings.get("connection.ssh.enabled"))) {
            throw new DataSourceException(error, null,
                    new UnsupportedOperationException("SSH connections are unavailable without the deleted runtime"));
        }
        try {
            String driver = (String) settings.get("connection.driver-class");
            if (driver != null && !driver.isBlank()) Class.forName(driver);
            Properties connectionProperties = new Properties();
            Object extra = settings.get("connection.properties");
            if (extra instanceof Map<?, ?> entries) {
                entries.forEach((key, value) -> {
                    if (key instanceof String k && value != null) {
                        connectionProperties.setProperty(k, value.toString());
                    }
                });
            }
            if (settings.get("connection.username") instanceof String name) {
                connectionProperties.setProperty("user", name);
            }
            if (settings.get("connection.password") instanceof String secret) {
                connectionProperties.setProperty("password", secret);
            }
            try (Connection connection = DriverManager.getConnection(
                    (String) settings.get("connection.url"), connectionProperties)) {
                return action.execute(connection);
            }
        } catch (Exception failure) {
            throw new DataSourceException(error, null, failure);
        }
    }

    @FunctionalInterface
    private interface CatalogAction<T> {
        T execute(Connection connection) throws SQLException;
    }
}
