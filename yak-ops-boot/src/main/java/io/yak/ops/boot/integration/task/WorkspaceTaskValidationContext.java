package io.yak.ops.boot.integration.task;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.Objects;
import java.util.Optional;

/** Read-only Task validation view of one authorized Workspace. */
final class WorkspaceTaskValidationContext implements TaskValidationContext {

    private final String workspaceId;
    private final DataSourceLookupService resources;

    WorkspaceTaskValidationContext(String workspaceId, DataSourceLookupService resources) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public DataSourceInfo dataSource(String id) {
        return resources.dataSource(workspaceId, id);
    }

    @Override
    public boolean samePhysicalServer(String firstDatasourceId, String secondDatasourceId) {
        return resources.samePhysicalServer(workspaceId, firstDatasourceId, secondDatasourceId);
    }

    @Override
    public DataSourceTable tableMetadata(String datasourceId, TableId tableId) {
        return resources.table(workspaceId, datasourceId, tableId.catalog(), tableId.schema(), tableId.table());
    }

    @Override
    public Optional<DataSourceTable> findTable(String datasourceId, TableId tableId) {
        return resources.findTable(workspaceId, datasourceId, tableId.catalog(), tableId.schema(), tableId.table());
    }

    @Override
    public TableSchema tableSchema(String datasourceId, TableId tableId) {
        return resources
                .table(workspaceId, datasourceId, tableId.catalog(), tableId.schema(), tableId.table())
                .schema();
    }
}
