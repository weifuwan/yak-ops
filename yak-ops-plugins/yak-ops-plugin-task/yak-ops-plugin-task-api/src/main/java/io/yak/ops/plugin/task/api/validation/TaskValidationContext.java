package io.yak.ops.plugin.task.api.validation;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.Optional;

/**
 * Read-only Workspace-scoped resource lookup exposed to Task Plugin validation.
 *
 * <p>Plugins receive no JDBC URL, password, connection or mutable DAO objects.
 * Failed catalog lookups must propagate rather than being interpreted as missing tables.
 */
public interface TaskValidationContext {

    /** Resolves an authorized Datasource and its optional bound namespace. */
    DataSourceInfo dataSource(String id);

    /**
     * Verifies whether two authorized datasource references share one physical server.
     *
     * @param firstDatasourceId first datasource in this context's Workspace
     * @param secondDatasourceId second datasource in this context's Workspace
     * @return whether both references resolve to the same physical server
     */
    default boolean samePhysicalServer(String firstDatasourceId, String secondDatasourceId) {
        throw new UnsupportedOperationException("Physical server identity verification is unavailable");
    }

    /**
     * Looks up the physical columns and keys of the requested table.
     *
     * @param datasourceId authorized Datasource identity
     * @param tableId explicit catalog, schema and table identifiers, not a raw SQL path
     */
    TableSchema tableSchema(String datasourceId, TableId tableId);

    /**
     * Reads the physical table and its schema-relevant metadata without connection credentials.
     *
     * @param datasourceId authorized Datasource identity
     * @param tableId exact table identity
     * @return immutable table metadata
     */
    default DataSourceTable tableMetadata(String datasourceId, TableId tableId) {
        var info = dataSource(datasourceId);
        return new DataSourceTable(info.type(), tableId, tableSchema(datasourceId, tableId), info.revision());
    }

    /**
     * Distinguishes an absent target from a failed metadata lookup.
     *
     * @param datasourceId authorized Datasource identity
     * @param tableId exact table identity
     * @return physical table metadata, or empty only for an absent object
     */
    default Optional<DataSourceTable> findTable(String datasourceId, TableId tableId) {
        return Optional.of(tableMetadata(datasourceId, tableId));
    }
}
