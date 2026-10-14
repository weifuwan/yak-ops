package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/** Immutable creation inputs for one table Source; contains no platform services or identity. */
public final class TableSourceFactoryContext {
    private final ReadonlyConfig options;
    private final TableMetrics metrics;
    private final TableId table;
    private final TableSchema schema;

    /**
     * Creates Source inputs without opening a connection.
     * @param options effective Connector settings, including resolved connection settings
     * @param table table identity
     * @param schema input table schema
     */
    public TableSourceFactoryContext(ReadonlyConfig options, TableId table, TableSchema schema) {
        this(options, table, schema, new TableMetrics());
    }

    /** Creates resolved Source inputs with the physical attempt's table observations. */
    public TableSourceFactoryContext(ReadonlyConfig options, TableId table, TableSchema schema, TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.options = Objects.requireNonNull(options, "options");
        this.table = Objects.requireNonNull(table, "table");
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    /** Returns the non-secret attempt-local measurement collector. */
    public TableMetrics metrics() {
        return metrics;
    }

    public ReadonlyConfig options() {
        return options;
    }

    public TableId table() {
        return table;
    }

    public TableSchema schema() {
        return schema;
    }

    @Override
    public String toString() {
        return "TableSourceFactoryContext{configuration=redacted}";
    }
}
