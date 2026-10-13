package io.yak.ops.plugin.datasource.api.lookup;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/** Non-secret physical metadata and revision for one authorized table. */
public record DataSourceTable(String datasourceType, TableId table, TableSchema schema, long revision) {

    public DataSourceTable {
        Objects.requireNonNull(datasourceType, "datasourceType");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(schema, "schema");
        if (revision < 1) {
            throw new IllegalArgumentException("Datasource revision is required");
        }
    }
}
