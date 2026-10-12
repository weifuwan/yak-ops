package io.yak.ops.plugin.task.datasync;

/** Trusted Workspace-scoped datasource and metadata access for DATA_SYNC execution. */
public interface SyncRuntimeResources {

    /**
     * Resolves the exact authorized table without exposing connection credentials.
     *
     * @param workspaceId authorized Workspace of the admitted Instance
     * @param datasourceId saved datasource identifier
     * @param database optional selected database
     * @param schema optional selected schema
     * @param table selected table name
     * @return canonical table identity, schema and binding revision
     */
    SyncRuntimeTable table(String workspaceId, String datasourceId, String database, String schema, String table);

    /**
     * Resolves runtime connection capability under the persisted Workspace scope.
     *
     * @param workspaceId authorized Workspace of the admitted Instance
     * @param datasourceId saved datasource identifier
     * @return trusted connection and its binding revision; never persist this object
     */
    SyncRuntimeConnection connection(String workspaceId, String datasourceId);
}
