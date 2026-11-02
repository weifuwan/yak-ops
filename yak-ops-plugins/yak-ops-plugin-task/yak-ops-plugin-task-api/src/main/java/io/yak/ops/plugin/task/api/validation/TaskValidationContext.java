package io.yak.ops.plugin.task.api.validation;

import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;

/** Read-only Workspace-scoped resource lookup exposed to Task plugins. */
public interface TaskValidationContext {
    DataSourceInfo dataSource(String id);

    default boolean samePhysicalServer(String firstDatasourceId, String secondDatasourceId) {
        throw new UnsupportedOperationException("Physical server identity verification is unavailable");
    }
}
