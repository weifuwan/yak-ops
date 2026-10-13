package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/** Immutable table and configuration inputs used to construct a Sink. */
public final class TableSinkFactoryContext {
    private final ReadonlyConfig options;
    private final TableId sourceTable;
    private final TableSchema sourceSchema;
    private final TableId targetTable;
    private final TableSchema targetSchema;

    /**
     * Creates Sink inputs without opening a connection.
     * @param options effective Connector settings
     * @param sourceTable input table identity
     * @param sourceSchema input row schema
     * @param targetTable destination table identity
     * @param targetSchema destination table schema
     */
    public TableSinkFactoryContext(
            ReadonlyConfig options,
            TableId sourceTable,
            TableSchema sourceSchema,
            TableId targetTable,
            TableSchema targetSchema) {
        this.options = Objects.requireNonNull(options, "options");
        this.sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
        this.sourceSchema = Objects.requireNonNull(sourceSchema, "sourceSchema");
        this.targetTable = Objects.requireNonNull(targetTable, "targetTable");
        this.targetSchema = Objects.requireNonNull(targetSchema, "targetSchema");
    }

    public ReadonlyConfig options() {
        return options;
    }

    public TableId sourceTable() {
        return sourceTable;
    }

    public TableSchema sourceSchema() {
        return sourceSchema;
    }

    public TableId targetTable() {
        return targetTable;
    }

    public TableSchema targetSchema() {
        return targetSchema;
    }

    @Override
    public String toString() {
        return "TableSinkFactoryContext{configuration=redacted}";
    }
}
