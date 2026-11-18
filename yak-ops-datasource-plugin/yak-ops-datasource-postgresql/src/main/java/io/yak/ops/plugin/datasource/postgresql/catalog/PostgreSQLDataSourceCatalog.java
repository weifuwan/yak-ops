package io.yak.ops.plugin.datasource.postgresql.catalog;

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
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import io.yak.ops.plugin.datasource.api.catalog.NamespaceLevel;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** PostgreSQL metadata discovery backed by JDBC metadata and PostgreSQL catalogs. */
public class PostgreSQLDataSourceCatalog implements DataSourceCatalog {

    private static final String JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/yak_ops";

    @Override
    public List<ConnectionProperty> getConnectionProperties() {
        DriverPropertyInfo[] properties = new org.postgresql.Driver()
                .getPropertyInfo(JDBC_URL, new Properties());
        List<ConnectionProperty> result = new ArrayList<>();
        for (DriverPropertyInfo property : properties) {
            if (property.name == null || "user".equalsIgnoreCase(property.name)
                    || "password".equalsIgnoreCase(property.name)) continue;
            result.add(new ConnectionProperty(property.name,
                    property.description == null ? "" : property.description,
                    property.value, property.required,
                    property.choices == null ? Collections.emptyList() : Arrays.asList(property.choices)));
        }
        return result;
    }

    @Override
    public CatalogCapabilities getCapabilities() {
        return new CatalogCapabilities(
                Arrays.asList(NamespaceLevel.DATABASE, NamespaceLevel.SCHEMA),
                Arrays.asList(CatalogObjectType.TABLE, CatalogObjectType.VIEW, CatalogObjectType.MATERIALIZED_VIEW),
                Arrays.asList(CatalogFeature.COLUMNS, CatalogFeature.INDEXES, CatalogFeature.PRIMARY_KEYS));
    }

    @Override
    public List<CatalogNamespace> listNamespaces(Connection connection, CatalogPath parentPath, NamespaceLevel level) {
        String sql;
        if (level == NamespaceLevel.DATABASE) {
            sql = "SELECT datname FROM pg_database WHERE datallowconn = TRUE"
                    + " AND datistemplate = FALSE ORDER BY datname";
        } else if (level == NamespaceLevel.SCHEMA) {
            sql = "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name";
        } else {
            throw new UnsupportedOperationException("PostgreSQL does not expose a catalog namespace level");
        }
        List<CatalogNamespace> namespaces = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String name = result.getString(1);
                CatalogPath path = level == NamespaceLevel.DATABASE
                        ? new CatalogPath(null, name, null, null, null)
                        : new CatalogPath(null, parentPath == null ? connection.getCatalog() : parentPath.getDatabase(),
                                name, null, null);
                namespaces.add(new CatalogNamespace(name, level, path, true));
            }
            return namespaces;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list PostgreSQL namespaces", e);
        }
    }

    @Override
    public CatalogPage<CatalogObject> listObjects(Connection connection, CatalogObjectQuery query) {
        CatalogPath path = query.getPath();
        if (path == null || path.getSchema() == null) {
            throw new IllegalArgumentException("A schema is required to list PostgreSQL objects");
        }
        CatalogObjectType type = query.getObjectType();
        if (type != null && type != CatalogObjectType.TABLE && type != CatalogObjectType.VIEW
                && type != CatalogObjectType.MATERIALIZED_VIEW) {
            return new CatalogPage<>(Collections.<CatalogObject>emptyList(), 0);
        }
        boolean materialized = type == CatalogObjectType.MATERIALIZED_VIEW;
        String source = materialized ? " FROM pg_matviews WHERE schemaname = ?"
                : " FROM information_schema.tables WHERE table_schema = ?"
                    + (type == CatalogObjectType.TABLE ? " AND table_type = 'BASE TABLE'"
                    : type == CatalogObjectType.VIEW ? " AND table_type = 'VIEW'"
                    : " AND table_type IN ('BASE TABLE', 'VIEW')");
        String nameColumn = materialized ? "matviewname" : "table_name";
        String keywordFilter = query.getKeyword() == null || query.getKeyword().trim().isEmpty()
                ? "" : " AND " + nameColumn + " ILIKE ?";
        String countSql = "SELECT COUNT(*)" + source + keywordFilter;
        String selectSql = "SELECT " + nameColumn + source + keywordFilter
                + " ORDER BY " + nameColumn + " LIMIT ? OFFSET ?";
        try {
            long total;
            try (PreparedStatement count = connection.prepareStatement(countSql)) {
                count.setString(1, path.getSchema());
                if (!keywordFilter.isEmpty()) count.setString(2, "%" + query.getKeyword().trim() + "%");
                try (ResultSet result = count.executeQuery()) { total = result.next() ? result.getLong(1) : 0; }
            }
            List<CatalogObject> objects = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                int index = 1;
                statement.setString(index++, path.getSchema());
                if (!keywordFilter.isEmpty()) statement.setString(index++, "%" + query.getKeyword().trim() + "%");
                statement.setInt(index++, query.getLimit());
                statement.setInt(index, query.getOffset());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String name = result.getString(1);
                        CatalogObjectType objectType = materialized ? CatalogObjectType.MATERIALIZED_VIEW
                                : type == CatalogObjectType.VIEW ? CatalogObjectType.VIEW : CatalogObjectType.TABLE;
                        CatalogPath objectPath = new CatalogPath(path.getCatalog(), path.getDatabase(),
                                path.getSchema(), name, objectType);
                        objects.add(new CatalogObject(name, objectType, null, objectPath, true,
                                Collections.<String>emptyList()));
                    }
                }
            }
            return new CatalogPage<>(objects, total);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list PostgreSQL catalog objects", e);
        }
    }

    @Override
    public CatalogObject getObject(Connection connection, CatalogPath path) {
        CatalogPage<CatalogObject> page = listObjects(connection,
                new CatalogObjectQuery(path, path.getObjectType(), path.getObjectName(), 0, 100));
        for (CatalogObject object : page.getItems()) {
            if (path.getObjectName().equals(object.getName())) {
                List<String> primaryKeys = new ArrayList<>();
                try (ResultSet keys = connection.getMetaData().getPrimaryKeys(
                        path.getDatabase(), path.getSchema(), path.getObjectName())) {
                    while (keys.next()) primaryKeys.add(keys.getString("COLUMN_NAME"));
                } catch (SQLException e) {
                    throw new IllegalStateException("Failed to load PostgreSQL primary keys", e);
                }
                return new CatalogObject(object.getName(), object.getType(), object.getComment(),
                        object.getPath(), true, primaryKeys);
            }
        }
        return null;
    }

    @Override
    public List<DataSourceCatalogColumn> listColumns(Connection connection, CatalogPath path) {
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            List<String> primaryKeys = new ArrayList<>();
            try (ResultSet keys = metadata.getPrimaryKeys(path.getDatabase(), path.getSchema(), path.getObjectName())) {
                while (keys.next()) primaryKeys.add(keys.getString("COLUMN_NAME"));
            }
            List<DataSourceCatalogColumn> columns = new ArrayList<>();
            try (ResultSet result = metadata.getColumns(path.getDatabase(), path.getSchema(), path.getObjectName(), "%")) {
                while (result.next()) {
                    String name = result.getString("COLUMN_NAME");
                    columns.add(new DataSourceCatalogColumn(name, result.getString("TYPE_NAME"),
                            result.getInt("DATA_TYPE"), result.getInt("COLUMN_SIZE"),
                            result.getInt("DECIMAL_DIGITS"),
                            result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            result.getInt("ORDINAL_POSITION"), primaryKeys.contains(name),
                            result.getString("REMARKS")));
                }
            }
            return columns;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load PostgreSQL columns", e);
        }
    }

    @Override
    public List<CatalogIndex> listIndexes(Connection connection, CatalogPath path) {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        Map<String, Boolean> unique = new LinkedHashMap<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(
                path.getDatabase(), path.getSchema(), path.getObjectName(), false, false)) {
            while (result.next()) {
                String name = result.getString("INDEX_NAME");
                String column = result.getString("COLUMN_NAME");
                if (name == null || column == null) continue;
                columns.computeIfAbsent(name, ignored -> new ArrayList<>()).add(column);
                unique.put(name, !result.getBoolean("NON_UNIQUE"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load PostgreSQL indexes", e);
        }
        List<CatalogIndex> indexes = new ArrayList<>();
        columns.forEach((name, values) -> indexes.add(new CatalogIndex(
                name, values, Boolean.TRUE.equals(unique.get(name)), name.toLowerCase().endsWith("_pkey"))));
        return indexes;
    }

    @Override
    public List<CatalogObject> searchObjects(Connection connection, String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) return Collections.emptyList();
        String sql = "SELECT table_schema, table_name, table_type FROM information_schema.tables"
                + " WHERE table_name ILIKE ? ORDER BY table_schema, table_name LIMIT ?";
        List<CatalogObject> objects = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + keyword.trim() + "%");
            statement.setInt(2, Math.max(1, Math.min(limit, 200)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String schema = result.getString("table_schema");
                    String name = result.getString("table_name");
                    CatalogObjectType type = "VIEW".equalsIgnoreCase(result.getString("table_type"))
                            ? CatalogObjectType.VIEW : CatalogObjectType.TABLE;
                    CatalogPath path = new CatalogPath(null, connection.getCatalog(), schema, name, type);
                    objects.add(new CatalogObject(name, type, null, path, true,
                            Collections.<String>emptyList()));
                }
            }
            return objects;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to search PostgreSQL catalog", e);
        }
    }
}
