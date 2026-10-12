package io.yak.ops.boot.config;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.core.data.TableId;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import io.yak.ops.plugin.task.datasync.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.SyncRuntimeTable;
import jakarta.annotation.Resource;
import java.net.URI;
import java.sql.SQLException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Resolves runtime connections through the authorized Workspace and Datasource plugin.
 *
 * <p>Background workers use the persisted Workspace ID and do not inherit request ThreadLocals.
 */
@Component
public class DataSyncRuntimeResources implements SyncRuntimeResources {

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourceProperties properties;

    @Override
    public SyncRuntimeTable table(
            String workspaceId, String datasourceId, String database, String schema, String table) {
        Binding binding = resolve(workspaceId, datasourceId);
        TableId selected = new TableId(
                choose(binding.connection.database(), database),
                choose(binding.connection.schema(), schema),
                Objects.requireNonNull(table, "table"));
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(binding.connection.jdbcUrl(), provider(binding))) {
            return new SyncRuntimeTable(
                    binding.entity.getDbType(), selected, catalog.getTable(selected), binding.entity.getRevision());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to resolve DATA_SYNC table metadata", exception);
        }
    }

    @Override
    public SyncRuntimeConnection connection(String workspaceId, String datasourceId) {
        Binding binding = resolve(workspaceId, datasourceId);
        String host = null;
        int port = 3306;
        boolean tunneled = false;
        if (binding.connection instanceof JdbcConnectionProperties jdbc) {
            host = jdbc.host();
            port = jdbc.port();
            tunneled = jdbc.sshTunnel().enabled();
        }
        if (host == null && binding.connection.jdbcUrl().startsWith("jdbc:mysql://")) {
            URI url = URI.create(binding.connection.jdbcUrl().substring(5));
            host = url.getHost();
            if (url.getPort() > 0) {
                port = url.getPort();
            }
        }
        return new SyncRuntimeConnection(
                provider(binding),
                binding.connection.jdbcUrl(),
                binding.entity.getDbType(),
                binding.connection.username(),
                binding.connection.password(),
                host,
                port,
                binding.entity.getRevision(),
                tunneled);
    }

    private Binding resolve(String workspaceId, String datasourceId) {
        DataSourceEntity entity = repository
                .queryById(Objects.requireNonNull(workspaceId, "workspaceId"), datasourceId)
                .orElseThrow(() -> new IllegalArgumentException("Datasource is not available in this Workspace"));
        DataSourcePlugin plugin = DataSourcePluginRegistry.get(entity.getDbType());
        DataSourceConnection connection = plugin.parseConnection(entity.getConnectionParams());
        if (entity.getRevision() == null || entity.getRevision() < 1) {
            throw new IllegalStateException("Datasource revision must be initialized");
        }
        if (connection.jdbcUrl() == null || !connection.jdbcUrl().startsWith("jdbc:")) {
            throw new IllegalArgumentException("DATA_SYNC requires a JDBC datasource");
        }
        return new Binding(entity, plugin, connection);
    }

    private JdbcConnectionProvider provider(Binding binding) {
        return () -> {
            try {
                return binding.plugin.openConnection(
                        binding.connection, Math.max(1, properties.getConnectionTestTimeoutSeconds()));
            } catch (SQLException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new SQLException("Unable to open registered datasource connection", exception);
            }
        };
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

    private record Binding(DataSourceEntity entity, DataSourcePlugin plugin, DataSourceConnection connection) {}
}
