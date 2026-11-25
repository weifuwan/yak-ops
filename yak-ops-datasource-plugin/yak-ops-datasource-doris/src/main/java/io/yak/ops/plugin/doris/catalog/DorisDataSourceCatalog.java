package io.yak.ops.plugin.doris.catalog;

import io.yak.ops.plugin.datasource.api.catalog.CatalogCapabilities;
import io.yak.ops.plugin.datasource.api.catalog.CatalogFeature;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Doris JDBC metadata exposed through the shared datasource catalog contract. */
public class DorisDataSourceCatalog implements DataSourceCatalog {

    @Override
    public List<ConnectionProperty> getConnectionProperties() {
        return Collections.emptyList();
    }

    @Override
    public CatalogCapabilities getCapabilities() {
        return new CatalogCapabilities(
                Collections.singletonList(NamespaceLevel.DATABASE),
                Collections.singletonList(CatalogObjectType.TABLE),
                Arrays.asList(CatalogFeature.COLUMNS, CatalogFeature.PRIMARY_KEYS, CatalogFeature.COMMENTS));
    }

    @Override
    public List<CatalogNamespace> listNamespaces(
            Connection connection, CatalogPath parentPath, NamespaceLevel level) {
        if (level != NamespaceLevel.DATABASE) {
            throw new UnsupportedOperationException("Doris exposes databases without a separate schema level");
        }
        String database = requireConfiguredDatabase(connection, null);
        return Collections.singletonList(new CatalogNamespace(
                database,
                NamespaceLevel.DATABASE,
                new CatalogPath(null, database, null, null, null),
                true));
    }

    @Override
    public CatalogPage<CatalogObject> listObjects(Connection connection, CatalogObjectQuery query) {
        CatalogPath path = query == null ? null : query.getPath();
        if (path == null || path.getDatabase() == null) {
            throw new IllegalArgumentException("A database is required to list Doris tables");
        }
        String database = requireConfiguredDatabase(connection, path.getDatabase());
        if (query.getObjectType() != null && query.getObjectType() != CatalogObjectType.TABLE) {
            return new CatalogPage<>(Collections.<CatalogObject>emptyList(), 0);
        }

        List<CatalogObject> objects = new ArrayList<>();
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(database, null, "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    if (name == null || (!keyword.isEmpty()
                            && !name.toLowerCase(java.util.Locale.ROOT)
                                    .contains(keyword.toLowerCase(java.util.Locale.ROOT)))) {
                        continue;
                    }
                    objects.add(new CatalogObject(
                            name,
                            CatalogObjectType.TABLE,
                            tables.getString("REMARKS"),
                            new CatalogPath(null, database, null, name, CatalogObjectType.TABLE),
                            true,
                            Collections.<String>emptyList()));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list Doris catalog tables", ex);
        }

        int from = Math.min(query.getOffset(), objects.size());
        int to = Math.min(from + query.getLimit(), objects.size());
        return new CatalogPage<>(objects.subList(from, to), objects.size());
    }

    @Override
    public CatalogObject getObject(Connection connection, CatalogPath path) {
        if (path == null || path.getDatabase() == null || path.getObjectName() == null) {
            throw new IllegalArgumentException("A database and table name are required");
        }
        String database = requireConfiguredDatabase(connection, path.getDatabase());
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(database, null, path.getObjectName(),
                    new String[] {"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (path.getObjectName().equalsIgnoreCase(tableName)) {
                        return new CatalogObject(
                                tableName,
                                CatalogObjectType.TABLE,
                                tables.getString("REMARKS"),
                                new CatalogPath(null, database, null, tableName, CatalogObjectType.TABLE),
                                true,
                                Collections.<String>emptyList());
                    }
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read Doris table metadata", ex);
        }
    }

    @Override
    public List<DataSourceCatalogColumn> listColumns(Connection connection, CatalogPath path) {
        if (path == null) {
            throw new IllegalArgumentException("A Doris table path is required");
        }
        return listColumns(connection, path.getDatabase(), path.getObjectName());
    }

    @Override
    public List<DataSourceCatalogColumn> listColumns(
            Connection connection, String database, String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("A Doris table name is required");
        }
        String configuredDatabase = requireConfiguredDatabase(connection, database);
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> primaryKeys = new HashSet<>(readPrimaryKeys(metadata, configuredDatabase, tableName));
            List<DataSourceCatalogColumn> columns = new ArrayList<>();
            try (ResultSet result = metadata.getColumns(configuredDatabase, null, tableName, null)) {
                while (result.next()) {
                    String actualTable = result.getString("TABLE_NAME");
                    if (!tableName.equalsIgnoreCase(actualTable)) {
                        continue;
                    }
                    String name = result.getString("COLUMN_NAME");
                    if (name == null || name.trim().isEmpty()) {
                        continue;
                    }
                    int jdbcTypeValue = result.getInt("DATA_TYPE");
                    Integer jdbcType = result.wasNull() ? null : jdbcTypeValue;
                    int sizeValue = result.getInt("COLUMN_SIZE");
                    Integer size = result.wasNull() ? null : sizeValue;
                    int scaleValue = result.getInt("DECIMAL_DIGITS");
                    Integer scale = result.wasNull() ? null : scaleValue;
                    int nullableValue = result.getInt("NULLABLE");
                    Boolean nullable = result.wasNull()
                            ? null : nullableValue != DatabaseMetaData.columnNoNulls;
                    int ordinalValue = result.getInt("ORDINAL_POSITION");
                    Integer ordinal = result.wasNull() ? null : ordinalValue;
                    columns.add(new DataSourceCatalogColumn(
                            name,
                            result.getString("TYPE_NAME"),
                            jdbcType,
                            size,
                            scale,
                            nullable,
                            ordinal,
                            primaryKeys.contains(name),
                            result.getString("REMARKS")));
                }
            }
            return columns;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read Doris table columns", ex);
        }
    }

    private List<String> readPrimaryKeys(DatabaseMetaData metadata, String database, String tableName)
            throws SQLException {
        List<String> keys = new ArrayList<>();
        try (ResultSet result = metadata.getPrimaryKeys(database, null, tableName)) {
            while (result.next()) {
                String name = result.getString("COLUMN_NAME");
                if (name != null && !name.trim().isEmpty()) {
                    keys.add(name);
                }
            }
        }
        return keys;
    }

    private String requireConfiguredDatabase(Connection connection, String requestedDatabase) {
        try {
            String configuredDatabase = connection.getCatalog();
            if (configuredDatabase == null || configuredDatabase.trim().isEmpty()) {
                throw new IllegalStateException("The Doris datasource has no configured database");
            }
            if (requestedDatabase != null && !configuredDatabase.equals(requestedDatabase)) {
                throw new IllegalArgumentException(
                        "Catalog database '" + requestedDatabase
                                + "' is outside the configured Doris datasource database '"
                                + configuredDatabase + "'");
            }
            return configuredDatabase;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to resolve configured Doris database", ex);
        }
    }
}
