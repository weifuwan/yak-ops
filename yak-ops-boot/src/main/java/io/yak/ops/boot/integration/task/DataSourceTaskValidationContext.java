package io.yak.ops.boot.integration.task;

import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.api.validation.TaskValidationContextFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** Binds interactive Definition validation to the authenticated Workspace. */
@Component
public class DataSourceTaskValidationContext implements TaskValidationContext {
    @Resource private TaskValidationContextFactory contexts;

    @Override
    public DataSourceInfo dataSource(String id) {
        return contexts.forWorkspace(WorkspaceContext.requireWorkspaceId()).dataSource(id);
    }

    @Override
    public boolean samePhysicalServer(String firstDatasourceId, String secondDatasourceId) {
        return contexts.forWorkspace(WorkspaceContext.requireWorkspaceId())
                .samePhysicalServer(firstDatasourceId, secondDatasourceId);
    }
}
