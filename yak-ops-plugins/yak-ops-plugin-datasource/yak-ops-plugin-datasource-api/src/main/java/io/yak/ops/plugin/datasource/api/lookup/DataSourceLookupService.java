package io.yak.ops.plugin.datasource.api.lookup;

import io.yak.ops.configuration.ReadonlyConfig;

/** Resolves saved datasource settings under an authorized Workspace identity. */
public interface DataSourceLookupService {
    DataSourceInfo dataSource(String workspaceId, String datasourceId);

    default boolean samePhysicalServer(String workspaceId, String firstDatasourceId, String secondDatasourceId) {
        throw new UnsupportedOperationException("Physical server identity verification is unavailable");
    }

    ReadonlyConfig connectionOptions(String workspaceId, String datasourceId);

    ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision);
}
