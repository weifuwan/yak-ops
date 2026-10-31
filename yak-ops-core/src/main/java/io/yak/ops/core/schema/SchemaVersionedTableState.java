package io.yak.ops.core.schema;

import io.yak.ops.core.data.TableId;
import java.io.Serializable;
import java.util.Objects;

/** Committed source/target schema, including version zero before the first DDL transition. */
public record SchemaVersionedTableState(
        TableId sourceTable, TableId targetTable, Schema sourceSchema, Schema targetSchema, long version)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public SchemaVersionedTableState {
        sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
        targetTable = Objects.requireNonNull(targetTable, "targetTable");
        sourceSchema = Objects.requireNonNull(sourceSchema, "sourceSchema");
        targetSchema = Objects.requireNonNull(targetSchema, "targetSchema");
        if (version < 0) {
            throw new IllegalArgumentException("Schema version must not be negative");
        }
    }
}
