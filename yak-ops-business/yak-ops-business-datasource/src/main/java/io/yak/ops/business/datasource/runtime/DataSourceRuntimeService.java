package io.yak.ops.business.datasource.runtime;

/** Provides trusted Workspace-scoped JDBC metadata and connections for product execution. */
public interface DataSourceRuntimeService {

    /**
     * Resolves one table in the registered Datasource namespace.
     *
     * @param workspaceId authorized Workspace identity
     * @param datasourceId registered Datasource identifier
     * @param database optional selected database
     * @param schema optional selected schema
     * @param table selected table name
     * @return canonical table metadata and binding revision
     */
    DataSourceRuntimeTable table(String workspaceId, String datasourceId, String database, String schema, String table);

    /**
     * Resolves a Datasource connection under the explicit Workspace identity.
     *
     * @param workspaceId authorized Workspace identity
     * @param datasourceId registered Datasource identifier
     * @return runtime connection capability and binding revision
     */
    DataSourceRuntimeConnection connection(String workspaceId, String datasourceId);
}
