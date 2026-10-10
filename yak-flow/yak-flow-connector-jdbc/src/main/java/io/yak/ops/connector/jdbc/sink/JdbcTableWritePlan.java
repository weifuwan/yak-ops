package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/**
 * Immutable single-source-table to single-target-table write mapping.
 *
 * <p>The row order is the target schema's column order. Transformations and multi-table
 * routing are outside the JDBC Writer. Only INSERT records are accepted in this phase.
 */
public record JdbcTableWritePlan(
        TableId sourceTable, TableId targetTable, TableSchema schema, JdbcWriteMode writeMode) {

    public JdbcTableWritePlan {
        Objects.requireNonNull(sourceTable, "sourceTable");
        Objects.requireNonNull(targetTable, "targetTable");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(writeMode, "writeMode");
        for (Column column : schema.columns()) {
            if (!column.dataType().isResolved()) {
                throw new IllegalArgumentException("JDBC write requires resolved types");
            }
        }
        if (writeMode == JdbcWriteMode.UPSERT && schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("JDBC UPSERT requires primary keys");
        }
    }

    public String sql(JdbcDialect dialect) {
        Objects.requireNonNull(dialect, "dialect");
        return switch (writeMode) {
            case APPEND -> dialect.insertSql(targetTable, schema);
            case UPSERT -> dialect.upsertSql(targetTable, schema);
        };
    }
}
