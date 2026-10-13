package io.yak.ops.plugin.task.api.validation;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;

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
     * Looks up the physical columns and keys of the requested table.
     *
     * @param datasourceId authorized Datasource identity
     * @param tableId explicit catalog, schema and table identifiers, not a raw SQL path
     */
    TableSchema tableSchema(String datasourceId, TableId tableId);
}
