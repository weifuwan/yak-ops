package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.MySqlServerIdentity;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import jakarta.annotation.Resource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Reads authorized datasource settings and opens caller-scoped Catalogs for metadata lookup. */
@Service
public class DataSourceLookupServiceImpl implements DataSourceLookupService {
    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourceProperties properties;

    @Override
    public DataSourceInfo dataSource(String workspaceId, String datasourceId) {
        DataSourceEntity entity = find(workspaceId, datasourceId);
        DataSourceConnection connection = parse(entity, plugin(entity));
        return new DataSourceInfo(entity.getDbType(), connection.database(), connection.schema(), entity.getRevision());
    }

    @Override
    public boolean samePhysicalServer(String workspaceId, String firstDatasourceId, String secondDatasourceId) {
        DataSourceEntity first = find(workspaceId, firstDatasourceId);
        DataSourceEntity second = find(workspaceId, secondDatasourceId);
        if (!"MYSQL".equals(first.getDbType()) || !"MYSQL".equals(second.getDbType())) {
            throw new UnsupportedOperationException("Physical identity verification requires MySQL datasources");
        }
        return MySqlServerIdentity.sameServer(connectionOptions(first), connectionOptions(second));
    }

    @Override
    public DataSourceTable table(
            String workspaceId, String datasourceId, String database, String schema, String table) {
        return findTable(workspaceId, datasourceId, database, schema, table)
                .orElseThrow(() -> new IllegalArgumentException("Datasource table is absent or not visible"));
    }

    @Override
    public Optional<DataSourceTable> findTable(
            String workspaceId, String datasourceId, String database, String schema, String table) {
        DataSourceEntity entity = find(workspaceId, datasourceId);
        DataSourcePlugin plugin = plugin(entity);
        DataSourceConnection connection = parse(entity, plugin);
        TableId selected = new DataSourceInfo(
                        entity.getDbType(), connection.database(), connection.schema(), entity.getRevision())
                .resolveTable(database, schema, Objects.requireNonNull(table, "table"));
        JdbcConnectionOptions options =
                new JdbcConnectionOptions(plugin.connectionOptions(connection, timeoutSeconds()));
        try (JdbcCatalog catalog =
                JdbcCatalogFactory.create(options.url(), new DefaultJdbcConnectionProvider(options))) {
            var physical = catalog.findTable(selected);
            if (physical.isEmpty()) return Optional.empty();
            var description = physical.orElseThrow();
            var columns = catalog.getColumns(selected);
            var comments = new HashMap<String, String>();
            var features = new HashSet<String>();
            Map<String, ColumnProperties> attributes = new HashMap<>();
            if (!"TABLE".equalsIgnoreCase(description.type()) && !"BASE TABLE".equalsIgnoreCase(description.type())) {
                features.add("NON_TABLE");
            }
            for (var column : columns) {
                if (column.remarks() != null) comments.put(column.name(), column.remarks());
                if (column.hasDefaultValue()) features.add("DEFAULT");
                if (Boolean.TRUE.equals(column.autoIncrement())) features.add("IDENTITY");
                if (Boolean.TRUE.equals(column.generated())) features.add("GENERATED");
                if (column.autoIncrement() == null || column.generated() == null) features.add("UNKNOWN_GENERATION");
                attributes.put(
                        column.name(),
                        new ColumnProperties(
                                column.defaultExpression(),
                                column.autoIncrement(),
                                column.generated(),
                                column.onUpdateExpression(),
                                column.defaultGenerated(),
                                column.nativeType()));
            }
            return Optional.of(new DataSourceTable(
                    entity.getDbType(),
                    selected,
                    catalog.getTable(selected),
                    entity.getRevision(),
                    description.remarks(),
                    comments,
                    features,
                    attributes,
                    catalog.getUniqueKeys(selected)));
        } catch (java.sql.SQLFeatureNotSupportedException failure) {
            // Only the column identifier is allowed through the public validation boundary.
            String message = failure.getMessage();
            var matcher = java.util.regex.Pattern.compile(
                            "^Unsupported JDBC type .+ for field ([A-Za-z_][A-Za-z_0-9]{0,127})$")
                    .matcher(message == null ? "" : message);
            if (matcher.matches()) {
                throw new IllegalArgumentException(
                        "Unsupported source JDBC type for column: " + matcher.group(1), failure);
            }
            throw new IllegalStateException("Unable to resolve datasource table metadata", failure);
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to resolve datasource table metadata", failure);
        }
    }

    @Override
    public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId) {
        return connectionOptions(find(workspaceId, datasourceId));
    }

    @Override
    public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision) {
        DataSourceEntity entity = find(workspaceId, datasourceId);
        if (expectedRevision < 1 || entity.getRevision() != expectedRevision) {
            throw new IllegalStateException("Datasource revision changed before execution");
        }
        return connectionOptions(entity);
    }

    private ReadonlyConfig connectionOptions(DataSourceEntity entity) {
        DataSourcePlugin plugin = plugin(entity);
        return plugin.connectionOptions(parse(entity, plugin), timeoutSeconds());
    }

    private DataSourceEntity find(String workspaceId, String datasourceId) {
        DataSourceEntity entity = repository
                .queryById(Objects.requireNonNull(workspaceId, "workspaceId"), datasourceId)
                .orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
        if (entity.getRevision() == null || entity.getRevision() < 1) {
            throw new IllegalStateException("Datasource revision must be initialized");
        }
        return entity;
    }

    private DataSourcePlugin plugin(DataSourceEntity entity) {
        try {
            return DataSourcePluginRegistry.get(entity.getDbType());
        } catch (IllegalArgumentException failure) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, null, failure);
        } catch (NoSuchElementException failure) {
            throw new DataSourceException(DataSourceErrorCode.PLUGIN_NOT_FOUND, null, failure);
        }
    }

    private DataSourceConnection parse(DataSourceEntity entity, DataSourcePlugin plugin) {
        DataSourceConnection connection;
        try {
            connection = plugin.parseConnection(ConfigParser.parseJson(entity.getConnectionParams()));
        } catch (RuntimeException failure) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, null, failure);
        }
        if (connection.jdbcUrl() == null || !connection.jdbcUrl().startsWith("jdbc:")) {
            throw new IllegalArgumentException("JDBC datasource required");
        }
        return connection;
    }

    private int timeoutSeconds() {
        return Math.max(1, properties.getConnectionTestTimeoutSeconds());
    }
}
