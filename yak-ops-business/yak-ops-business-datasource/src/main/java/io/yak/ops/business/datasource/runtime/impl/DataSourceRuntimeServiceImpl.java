package io.yak.ops.business.datasource.runtime.impl;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeConnection;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeService;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeTable;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import jakarta.annotation.Resource;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Resolves registered JDBC Datasources for requests and background execution using an explicit Workspace. */
@Service
public class DataSourceRuntimeServiceImpl implements DataSourceRuntimeService {

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourceProperties properties;

    @Override
    public DataSourceRuntimeTable table(
            String workspaceId, String datasourceId, String database, String schema, String table) {
        DataSourceRuntimeConnection binding = connection(workspaceId, datasourceId);
        TableId selected = new TableId(
                choose(binding.database(), database),
                choose(binding.schema(), schema),
                Objects.requireNonNull(table, "table"));
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(binding.jdbcUrl(), binding.provider())) {
            return new DataSourceRuntimeTable(
                    binding.datasourceType(), selected, catalog.getTable(selected), binding.revision());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to resolve Datasource table metadata", exception);
        }
    }

    @Override
    public DataSourceRuntimeConnection connection(String workspaceId, String datasourceId) {
        DataSourceEntity entity = repository
                .queryById(Objects.requireNonNull(workspaceId, "workspaceId"), datasourceId)
                .orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
        DataSourcePlugin plugin;
        try {
            plugin = DataSourcePluginRegistry.get(entity.getDbType());
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, null, exception);
        } catch (NoSuchElementException exception) {
            throw new DataSourceException(DataSourceErrorCode.PLUGIN_NOT_FOUND, null, exception);
        }

        DataSourceConnection connection;
        try {
            connection = plugin.parseConnection(entity.getConnectionParams());
        } catch (RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, null, exception);
        }
        if (entity.getRevision() == null || entity.getRevision() < 1) {
            throw new IllegalStateException("Datasource revision must be initialized");
        }
        if (connection.jdbcUrl() == null || !connection.jdbcUrl().startsWith("jdbc:")) {
            throw new IllegalArgumentException("DATA_SYNC requires a JDBC datasource");
        }
        return new DataSourceRuntimeConnection(
                plugin,
                connection,
                entity.getDbType(),
                entity.getRevision(),
                Math.max(1, properties.getConnectionTestTimeoutSeconds()));
    }

    private String choose(String bound, String requested) {
        if (bound != null
                && !bound.isBlank()
                && requested != null
                && !requested.isBlank()
                && !bound.equals(requested)) {
            throw new IllegalArgumentException("Selected namespace differs from datasource binding");
        }
        return requested == null || requested.isBlank() ? bound : requested;
    }
}
