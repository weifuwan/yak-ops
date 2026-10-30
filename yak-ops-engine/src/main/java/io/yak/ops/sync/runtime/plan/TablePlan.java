package io.yak.ops.sync.runtime.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.List;
import java.util.Objects;

/**
 * One authorized source-to-target route and its optional initial schema operation.
 *
 * <p>The target identity and revision exist independently of the physical target. A null
 * targetSchema means the target is absent; it must be prepared and read back before compilation.
 */
public final class TablePlan {
    private final ReadonlyConfig sourceParameters;
    private final ReadonlyConfig sinkParameters;
    private final DataSourceTable source;
    private final DataSourceInfo targetDatasource;
    private final TableId targetTable;
    private final TableSchema targetSchema;
    private final CreateTableEvent schemaChange;
    private final ReadonlyConfig sourceOptions;
    private final SchemaChangePlan schemaPlan;
    private final DataSourceTable targetMetadata;
    private final TableSchema writeSourceSchema;

    @JsonCreator
    public TablePlan(
            @JsonProperty("sourceParameters") ReadonlyConfig sourceParameters,
            @JsonProperty("sinkParameters") ReadonlyConfig sinkParameters,
            @JsonProperty("source") DataSourceTable source,
            @JsonProperty("targetDatasource") DataSourceInfo targetDatasource,
            @JsonProperty("targetTable") TableId targetTable,
            @JsonProperty("targetSchema") TableSchema targetSchema,
            @JsonProperty("schemaChange") CreateTableEvent schemaChange,
            @JsonProperty("sourceOptions") ReadonlyConfig sourceOptions,
            @JsonProperty("schemaPlan") SchemaChangePlan schemaPlan,
            @JsonProperty("targetMetadata") DataSourceTable targetMetadata,
            @JsonProperty("writeSourceSchema") TableSchema writeSourceSchema) {
        Objects.requireNonNull(sourceParameters, "sourceParameters");
        Objects.requireNonNull(sinkParameters, "sinkParameters");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(writeSourceSchema, "writeSourceSchema");
        Objects.requireNonNull(targetDatasource, "targetDatasource");
        Objects.requireNonNull(targetTable, "targetTable");
        Objects.requireNonNull(sourceOptions, "sourceOptions");
        if (targetSchema == null && schemaChange == null) {
            throw new IllegalArgumentException("An absent target requires schema preparation");
        }
        if (schemaChange != null && !targetTable.equals(schemaChange.tableId())) {
            throw new IllegalArgumentException("Schema event and target identities differ");
        }
        if (schemaPlan != null && !targetTable.equals(schemaPlan.tableId())) {
            throw new IllegalArgumentException("Schema plan and target identities differ");
        }
        if (targetMetadata != null
                && (!targetTable.equals(targetMetadata.table())
                        || targetMetadata.revision() != targetDatasource.revision())) {
            throw new IllegalArgumentException("Target metadata does not match the admitted route");
        }
        this.sourceParameters = sourceParameters;
        this.sinkParameters = sinkParameters;
        this.source = source;
        this.targetDatasource = targetDatasource;
        this.targetTable = targetTable;
        this.targetSchema = targetSchema;
        this.schemaChange = schemaChange;
        this.sourceOptions = sourceOptions;
        this.schemaPlan = schemaPlan;
        this.targetMetadata = targetMetadata;
        this.writeSourceSchema = writeSourceSchema;
    }

    @JsonProperty("sourceParameters")
    public ReadonlyConfig sourceParameters() {
        return sourceParameters;
    }

    @JsonProperty("sinkParameters")
    public ReadonlyConfig sinkParameters() {
        return sinkParameters;
    }

    @JsonProperty("source")
    public DataSourceTable source() {
        return source;
    }

    @JsonProperty("targetDatasource")
    public DataSourceInfo targetDatasource() {
        return targetDatasource;
    }

    @JsonProperty("targetTable")
    public TableId targetTable() {
        return targetTable;
    }

    @JsonProperty("targetSchema")
    public TableSchema targetSchema() {
        return targetSchema;
    }

    @JsonProperty("schemaChange")
    public CreateTableEvent schemaChange() {
        return schemaChange;
    }

    @JsonProperty("sourceOptions")
    public ReadonlyConfig sourceOptions() {
        return sourceOptions;
    }

    @JsonProperty("schemaPlan")
    public SchemaChangePlan schemaPlan() {
        return schemaPlan;
    }

    @JsonProperty("targetMetadata")
    public DataSourceTable targetMetadata() {
        return targetMetadata;
    }

    @JsonProperty("writeSourceSchema")
    public TableSchema writeSourceSchema() {
        return writeSourceSchema;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof TablePlan other)) return false;
        return java.util.Objects.equals(sourceParameters, other.sourceParameters)
                && java.util.Objects.equals(sinkParameters, other.sinkParameters)
                && java.util.Objects.equals(source, other.source)
                && java.util.Objects.equals(targetDatasource, other.targetDatasource)
                && java.util.Objects.equals(targetTable, other.targetTable)
                && java.util.Objects.equals(targetSchema, other.targetSchema)
                && java.util.Objects.equals(schemaChange, other.schemaChange)
                && java.util.Objects.equals(sourceOptions, other.sourceOptions)
                && java.util.Objects.equals(schemaPlan, other.schemaPlan)
                && java.util.Objects.equals(targetMetadata, other.targetMetadata)
                && java.util.Objects.equals(writeSourceSchema, other.writeSourceSchema);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                sourceParameters,
                sinkParameters,
                source,
                targetDatasource,
                targetTable,
                targetSchema,
                schemaChange,
                sourceOptions,
                schemaPlan,
                targetMetadata,
                writeSourceSchema);
    }

    /** Ordered physical changes for one target table. */
    public List<SchemaChangeEvent> schemaChanges() {
        return schemaPlan == null ? schemaChange == null ? List.of() : List.of(schemaChange) : schemaPlan.changes();
    }

    /** Changes only physical Source settings; routing and target admission remain unchanged. */
    public TablePlan withSourceOptions(ReadonlyConfig physical) {
        return new TablePlan(
                sourceParameters,
                sinkParameters,
                source,
                targetDatasource,
                targetTable,
                targetSchema,
                schemaChange,
                physical,
                schemaPlan,
                targetMetadata,
                writeSourceSchema);
    }

    /** Returns the admitted desired schema; creating the target does not change this value. */
    public TableSchema expectedTargetSchema() {
        return schemaChange == null ? targetSchema : schemaChange.getSchema();
    }

    /** Replaces only physical metadata after checking the pinned identity and datasource revision. */
    public TablePlan withTarget(DataSourceTable target) {
        if (!targetTable.equals(target.table())
                || target.revision() != targetDatasource.revision()
                || !target.datasourceType().equals(targetDatasource.type())) {
            throw new IllegalStateException("Target binding changed during schema preparation");
        }
        return new TablePlan(
                sourceParameters,
                sinkParameters,
                source,
                targetDatasource,
                targetTable,
                target.schema(),
                schemaChange,
                sourceOptions,
                schemaPlan,
                target,
                writeSourceSchema);
    }
}
