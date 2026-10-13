package io.yak.ops.business.datasource.runtime;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/** Canonical table metadata and the persisted Datasource binding revision. */
public record DataSourceRuntimeTable(String datasourceType, TableId table, TableSchema schema, long revision) {

    public DataSourceRuntimeTable {
        Objects.requireNonNull(datasourceType, "datasourceType");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(schema, "schema");
        if (revision < 1) {
            throw new IllegalArgumentException("Datasource revision must be initialized");
        }
    }
}
