package io.yak.ops.boot.integration.task;

import io.yak.ops.business.datasource.runtime.DataSourceRuntimeConnection;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeService;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeTable;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** Adapts Workspace-scoped Datasource capabilities to the DATA_SYNC execution plugin. */
@Component
public class DataSyncRuntimeResources implements SyncRuntimeResources {

    @Resource
    private DataSourceRuntimeService datasources;

    @Override
    public SyncRuntimeTable table(
            String workspaceId, String datasourceId, String database, String schema, String table) {
        DataSourceRuntimeTable selected = datasources.table(workspaceId, datasourceId, database, schema, table);
        return new SyncRuntimeTable(
                selected.datasourceType(), selected.table(), selected.schema(), selected.revision());
    }

    @Override
    public SyncRuntimeConnection connection(String workspaceId, String datasourceId) {
        DataSourceRuntimeConnection binding = datasources.connection(workspaceId, datasourceId);
        return new SyncRuntimeConnection(
                binding.provider(),
                binding.jdbcUrl(),
                binding.datasourceType(),
                binding.username(),
                binding.password(),
                binding.host(),
                binding.port(),
                binding.revision(),
                binding.tunneled());
    }
}
