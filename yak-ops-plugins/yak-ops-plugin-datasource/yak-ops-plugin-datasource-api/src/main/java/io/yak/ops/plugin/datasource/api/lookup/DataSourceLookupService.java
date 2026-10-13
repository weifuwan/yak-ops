package io.yak.ops.plugin.datasource.api.lookup;

import io.yak.ops.configuration.ReadonlyConfig;

/** Resolves saved datasource settings and metadata under an explicit Workspace identity. */
public interface DataSourceLookupService {
    /**
     * Looks up datasource identity and its bound namespace without exposing credentials.
     * @param workspaceId authorized Workspace
     * @param datasourceId saved datasource identifier
     * @return non-secret datasource information
     */
    DataSourceInfo dataSource(String workspaceId, String datasourceId);

    /**
     * Resolves a table and reads its physical schema.
     * @param workspaceId authorized Workspace
     * @param datasourceId saved datasource identifier
     * @param database selected database or null for the datasource default
     * @param schema selected schema or null for the datasource default
     * @param table table name
     * @return metadata and the revision used for the lookup
     */
    DataSourceTable table(String workspaceId, String datasourceId, String database, String schema, String table);

    /**
     * Resolves current connection settings for interactive Catalog access.
     * @param workspaceId authorized Workspace
     * @param datasourceId saved datasource identifier
     * @return immutable connection inputs, excluded from task persistence and diagnostics
     */
    ReadonlyConfig connectionOptions(String workspaceId, String datasourceId);

    /**
     * Resolves connection settings from the exact revision pinned during planning.
     * @param workspaceId authorized Workspace
     * @param datasourceId saved datasource identifier
     * @param expectedRevision planned datasource revision
     * @return connection inputs parsed from the checked record, without opening a connection
     * @throws IllegalStateException if the datasource revision has changed
     */
    ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision);
}
