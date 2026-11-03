package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import jakarta.annotation.Resource;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Resolves authorized datasource connections; physical table metadata belongs to the Catalog. */
@Service
public class DataSourceLookupServiceImpl implements DataSourceLookupService {
    @Resource private DataSourceEntityRepository repository;
    @Resource private DataSourceProperties properties;

    @Override
    public DataSourceInfo dataSource(String workspaceId, String datasourceId) {
        DataSourceEntity entity = find(workspaceId, datasourceId);
        DataSourceConnection connection = parse(entity, plugin(entity));
        return new DataSourceInfo(entity.getDbType(), connection.database(), connection.schema(), entity.getRevision());
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
