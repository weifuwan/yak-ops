package io.yak.ops.boot.integration.task;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.Objects;

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
    public TableSchema tableSchema(String datasourceId, TableId tableId) {
        return resources
                .table(workspaceId, datasourceId, tableId.catalog(), tableId.schema(), tableId.table())
                .schema();
    }
}
