package io.yak.ops.plugin.datasource.mysql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.CatalogCapabilities;
import io.yak.ops.plugin.datasource.api.catalog.CatalogFeature;
import io.yak.ops.plugin.datasource.api.catalog.CatalogIndex;
import io.yak.ops.plugin.datasource.api.catalog.CatalogNamespace;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObject;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectQuery;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPage;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.catalog.NamespaceLevel;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogTable;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MySQL metadata provided by the installed MySQL JDBC driver.
 */
public class MySQLDataSourceCatalog implements DataSourceCatalog {

    private static final String JDBC_URL = "jdbc:mysql://127.0.0.1:3306/yak_ops";
    private static final String TABLE_FILTER =
            " FROM information_schema.TABLES t"
                    + " WHERE t.TABLE_SCHEMA = ?"
                    + " AND t.TABLE_TYPE IN ('BASE TABLE', 'VIEW')";

    @Override
    public List<ConnectionProperty> getConnectionProperties() {
        try {
            DriverPropertyInfo[] properties = new com.mysql.cj.jdbc.Driver()
                    .getPropertyInfo(JDBC_URL, new Properties());
            List<ConnectionProperty> result = new ArrayList<>();
            for (DriverPropertyInfo property : properties) {
                if (property.name == null || isHidden(property.name)) {
                    continue;
                }
                result.add(new ConnectionProperty(
                        property.name,
                        property.description == null ? "" : property.description,
                        property.value,
                        property.required,
                        property.choices == null
                                ? Collections.emptyList()
                                : Arrays.asList(property.choices)));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read MySQL JDBC properties", e);
        }
    }


    @Override
    public CatalogCapabilities getCapabilities() {
        return new CatalogCapabilities(
                Arrays.asList(NamespaceLevel.DATABASE),
                Arrays.asList(CatalogObjectType.TABLE, CatalogObjectType.VIEW),
                Arrays.asList(CatalogFeature.COLUMNS, CatalogFeature.INDEXES, CatalogFeature.PRIMARY_KEYS));
    }

    @Override
    public List<CatalogNamespace> listNamespaces(
            Connection connection, CatalogPath parentPath, NamespaceLevel level) {
        if (level != NamespaceLevel.DATABASE) {
            throw new UnsupportedOperationException("MySQL exposes databases without a separate schema level");
        }
        List<CatalogNamespace> namespaces = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getCatalogs()) {
            while (result.next()) {
                String database = result.getString("TABLE_CAT");
                CatalogPath path = new CatalogPath(null, database, null, null, null);
                namespaces.add(new CatalogNamespace(database, NamespaceLevel.DATABASE, path, true));
            }
            return namespaces;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list MySQL databases", e);
        }
    }

    @Override
    public CatalogPage<CatalogObject> listObjects(Connection connection, CatalogObjectQuery query) {
        CatalogPath path = query.getPath();
        if (path == null || path.getDatabase() == null) {
            throw new IllegalArgumentException("A database is required to list MySQL objects");
        }
        CatalogObjectType type = query.getObjectType();
        if (type != null && type != CatalogObjectType.TABLE && type != CatalogObjectType.VIEW) {
            return new CatalogPage<>(Collections.<CatalogObject>emptyList(), 0);
        }
        String typeFilter = type == CatalogObjectType.TABLE
                ? " AND t.TABLE_TYPE = 'BASE TABLE'"
                : type == CatalogObjectType.VIEW
                        ? " AND t.TABLE_TYPE = 'VIEW'"
                        : " AND t.TABLE_TYPE IN ('BASE TABLE', 'VIEW')";
        String keywordFilter = hasKeyword(query.getKeyword()) ? " AND t.TABLE_NAME LIKE ?" : "";
        String fromWhere = " FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = ?" + typeFilter + keywordFilter;
        String countSql = "SELECT COUNT(*)" + fromWhere;
        String listSql = "SELECT t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT" + fromWhere
                + " ORDER BY t.TABLE_NAME LIMIT ? OFFSET ?";
        try {
            long total;
            try (PreparedStatement count = connection.prepareStatement(countSql)) {
                int index = 1;
                count.setString(index++, path.getDatabase());
                if (hasKeyword(query.getKeyword())) count.setString(index, "%" + query.getKeyword().trim() + "%");
                try (ResultSet result = count.executeQuery()) {
                    total = result.next() ? result.getLong(1) : 0;
                }
            }
            List<CatalogObject> objects = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(listSql)) {
                int index = 1;
                statement.setString(index++, path.getDatabase());
                if (hasKeyword(query.getKeyword())) statement.setString(index++, "%" + query.getKeyword().trim() + "%");
                statement.setInt(index++, query.getLimit());
                statement.setInt(index, query.getOffset());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String name = result.getString("TABLE_NAME");
                        String tableType = result.getString("TABLE_TYPE");
                        CatalogObjectType objectType = "VIEW".equalsIgnoreCase(tableType)
                                ? CatalogObjectType.VIEW : CatalogObjectType.TABLE;
                        CatalogPath objectPath = new CatalogPath(null, path.getDatabase(), null, name, objectType);
                        objects.add(new CatalogObject(name, objectType, result.getString("TABLE_COMMENT"),
                                objectPath, true, Collections.<String>emptyList()));
                    }
                }
            }
            return new CatalogPage<>(objects, total);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list MySQL catalog objects", e);
        }
    }

    @Override
    public CatalogObject getObject(Connection connection, CatalogPath path) {
        if (path == null || path.getDatabase() == null || path.getObjectName() == null) {
            throw new IllegalArgumentException("A database and object name are required");
        }
        CatalogPage<CatalogObject> page = listObjects(connection,
                new CatalogObjectQuery(path, path.getObjectType(), path.getObjectName(), 0, 100));
        for (CatalogObject object : page.getItems()) {
            if (path.getObjectName().equals(object.getName())) {
                List<String> primaryKeys = new ArrayList<>();
                try (ResultSet keys = connection.getMetaData().getPrimaryKeys(
                        path.getDatabase(), null, path.getObjectName())) {
                    while (keys.next()) primaryKeys.add(keys.getString("COLUMN_NAME"));
                } catch (SQLException e) {
                    throw new IllegalStateException("Failed to load MySQL primary keys", e);
                }
                return new CatalogObject(object.getName(), object.getType(), object.getComment(),
                        object.getPath(), true, primaryKeys);
            }
        }
        return null;
    }

    @Override
    public List<DataSourceCatalogColumn> listColumns(Connection connection, CatalogPath path) {
        return listColumns(connection, path.getDatabase(), path.getObjectName());
    }

    @Override
    public List<CatalogIndex> listIndexes(Connection connection, CatalogPath path) {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        Map<String, Boolean> unique = new LinkedHashMap<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(
                path.getDatabase(), null, path.getObjectName(), false, false)) {
            while (result.next()) {
                String name = result.getString("INDEX_NAME");
                String column = result.getString("COLUMN_NAME");
                if (name == null || column == null) continue;
                columns.computeIfAbsent(name, ignored -> new ArrayList<>()).add(column);
                unique.put(name, !result.getBoolean("NON_UNIQUE"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load MySQL indexes", e);
        }
        List<CatalogIndex> indexes = new ArrayList<>();
        columns.forEach((name, values) -> indexes.add(new CatalogIndex(
                name, values, Boolean.TRUE.equals(unique.get(name)), "PRIMARY".equalsIgnoreCase(name))));
        return indexes;
    }

    @Override
    public List<CatalogObject> searchObjects(Connection connection, String keyword, int limit) {
        if (!hasKeyword(keyword)) return Collections.emptyList();
        String sql = "SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, TABLE_COMMENT"
                + " FROM information_schema.TABLES WHERE TABLE_NAME LIKE ?"
                + " AND TABLE_TYPE IN ('BASE TABLE', 'VIEW') ORDER BY TABLE_SCHEMA, TABLE_NAME LIMIT ?";
        List<CatalogObject> objects = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + keyword.trim() + "%");
            statement.setInt(2, Math.max(1, Math.min(limit, 200)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String database = result.getString("TABLE_SCHEMA");
                    String name = result.getString("TABLE_NAME");
                    CatalogObjectType type = "VIEW".equalsIgnoreCase(result.getString("TABLE_TYPE"))
                            ? CatalogObjectType.VIEW : CatalogObjectType.TABLE;
                    CatalogPath path = new CatalogPath(null, database, null, name, type);
                    objects.add(new CatalogObject(name, type, result.getString("TABLE_COMMENT"),
                            path, true, Collections.<String>emptyList()));
                }
            }
            return objects;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to search MySQL catalog", e);
        }
    }

    @Override
    public boolean supportsTablePaging() {
        return true;
    }

    @Override
    public int countTables(Connection connection, String database, String keyword) {
        String filter = hasKeyword(keyword)
                ? TABLE_FILTER + " AND INSTR(t.TABLE_NAME, ?) > 0"
                : TABLE_FILTER;
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT COUNT(*)" + filter)) {
            statement.setString(1, database);
            if (hasKeyword(keyword)) {
                statement.setString(2, keyword.trim());
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count MySQL tables", e);
        }
    }

    @Override
    public List<DataSourceCatalogTable> listTables(
            Connection connection, String database, String keyword, int offset, int limit) {
        String keywordFilter = hasKeyword(keyword) ? " AND INSTR(t.TABLE_NAME, ?) > 0" : "";
        String sql = "SELECT t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT,"
                + " GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR ',') AS PRIMARY_KEYS"
                + " FROM information_schema.TABLES t"
                + " LEFT JOIN information_schema.KEY_COLUMN_USAGE k"
                + " ON k.TABLE_SCHEMA = t.TABLE_SCHEMA"
                + " AND k.TABLE_NAME = t.TABLE_NAME"
                + " AND k.CONSTRAINT_NAME = 'PRIMARY'"
                + " WHERE t.TABLE_SCHEMA = ?"
                + " AND t.TABLE_TYPE IN ('BASE TABLE', 'VIEW')"
                + keywordFilter
                + " GROUP BY t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT"
                + " ORDER BY t.TABLE_NAME LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, database);
            if (hasKeyword(keyword)) {
                statement.setString(index++, keyword.trim());
            }
            statement.setInt(index++, limit);
            statement.setInt(index, offset);
            try (ResultSet result = statement.executeQuery()) {
                return readTables(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list MySQL tables", e);
        }
    }

    @Override
    public List<DataSourceCatalogTable> getTables(
            Connection connection, String database, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = tableNames.stream().map(ignored -> "?").collect(Collectors.joining(","));
        String sql = "SELECT t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT,"
                + " GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR ',') AS PRIMARY_KEYS"
                + " FROM information_schema.TABLES t"
                + " LEFT JOIN information_schema.KEY_COLUMN_USAGE k"
                + " ON k.TABLE_SCHEMA = t.TABLE_SCHEMA"
                + " AND k.TABLE_NAME = t.TABLE_NAME"
                + " AND k.CONSTRAINT_NAME = 'PRIMARY'"
                + " WHERE t.TABLE_SCHEMA = ?"
                + " AND t.TABLE_NAME IN (" + placeholders + ")"
                + " AND t.TABLE_TYPE IN ('BASE TABLE', 'VIEW')"
                + " GROUP BY t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT"
                + " ORDER BY t.TABLE_NAME";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, database);
            for (String tableName : tableNames) {
                statement.setString(index++, tableName);
            }
            try (ResultSet result = statement.executeQuery()) {
                return readTables(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load MySQL table metadata", e);
        }
    }

    @Override
    public List<DataSourceCatalogColumn> listColumns(
            Connection connection, String database, String tableName) {
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> primaryKeys = new HashSet<>();
            try (ResultSet result = metadata.getPrimaryKeys(database, null, tableName)) {
                while (result.next()) {
                    primaryKeys.add(result.getString("COLUMN_NAME"));
                }
            }

            List<DataSourceCatalogColumn> columns = new ArrayList<>();
            try (ResultSet result = metadata.getColumns(database, null, tableName, "%")) {
                while (result.next()) {
                    String name = result.getString("COLUMN_NAME");
                    columns.add(new DataSourceCatalogColumn(
                            name,
                            result.getString("TYPE_NAME"),
                            result.getInt("DATA_TYPE"),
                            result.getInt("COLUMN_SIZE"),
                            result.getInt("DECIMAL_DIGITS"),
                            result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            result.getInt("ORDINAL_POSITION"),
                            primaryKeys.contains(name),
                            result.getString("REMARKS")));
                }
            }
            return columns;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load MySQL column metadata", e);
        }
    }

    private static List<DataSourceCatalogTable> readTables(ResultSet result) throws SQLException {
        List<DataSourceCatalogTable> tables = new ArrayList<>();
        while (result.next()) {
            String primaryKeys = result.getString("PRIMARY_KEYS");
            tables.add(new DataSourceCatalogTable(
                    result.getString("TABLE_NAME"),
                    result.getString("TABLE_TYPE"),
                    result.getString("TABLE_COMMENT"),
                    primaryKeys == null || primaryKeys.trim().isEmpty()
                            ? Collections.emptyList()
                            : Arrays.asList(primaryKeys.split(","))));
        }
        return tables;
    }

    private static boolean hasKeyword(String keyword) {
        return keyword != null && !keyword.trim().isEmpty();
    }

    private static boolean isHidden(String name) {
        return "user".equalsIgnoreCase(name)
                || "password".equalsIgnoreCase(name)
                || "allowLoadLocalInfile".equalsIgnoreCase(name)
                || "allowLocalInfile".equalsIgnoreCase(name)
                || "allowUrlInLocalInfile".equalsIgnoreCase(name)
                || "autoDeserialize".equalsIgnoreCase(name);
    }
}
