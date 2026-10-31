package io.yak.ops.plugin.datasource.api.lookup;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.Optional;

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
     * Compares physical server identities through authorized, read-only metadata queries.
     * Implementations must fail closed when they cannot verify the identity.
     *
     * @param workspaceId authorized Workspace shared by both saved datasource references
     * @param firstDatasourceId first saved datasource
     * @param secondDatasourceId second saved datasource
     * @return whether both connections resolve to the same physical server
     */
    default boolean samePhysicalServer(String workspaceId, String firstDatasourceId, String secondDatasourceId) {
        throw new UnsupportedOperationException("Physical server identity verification is unavailable");
    }

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
     * Looks up a physical table, distinguishing absence from authorization or metadata failures.
     *
     * @param workspaceId authorized Workspace
     * @param datasourceId saved datasource identifier
     * @param database selected database or null for the bound namespace
     * @param schema selected schema or null for the bound namespace
     * @param table unqualified table name
     * @return visible physical metadata, or empty only when no matching object was found
     */
    default Optional<DataSourceTable> findTable(
            String workspaceId, String datasourceId, String database, String schema, String table) {
        return Optional.of(table(workspaceId, datasourceId, database, schema, table));
    }

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
