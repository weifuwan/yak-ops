package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.TransformDef;
import io.yak.ops.composer.validation.SchemaValidator;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.OperationType;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.flink.cdc.common.pipeline.DecimalPrecisionMode;
import org.apache.flink.cdc.common.schema.Selectors;
import org.apache.flink.cdc.common.source.SupportedMetadataColumn;
import org.apache.flink.cdc.runtime.operators.transform.PostTransformChangeInfo;
import org.apache.flink.cdc.runtime.operators.transform.ProjectionColumn;
import org.apache.flink.cdc.runtime.operators.transform.TransformContext;
import org.apache.flink.cdc.runtime.operators.transform.TransformFilter;
import org.apache.flink.cdc.runtime.operators.transform.TransformFilterProcessor;
import org.apache.flink.cdc.runtime.operators.transform.TransformProjectionProcessor;
import org.apache.flink.cdc.runtime.parser.TransformParser;

/** Uses Flink CDC's SQL parser and expression processors in YakFlow mailbox operators. */
public final class TransformTranslator {
    private static final SupportedMetadataColumn[] METADATA = new SupportedMetadataColumn[0];
    private final List<TransformDef> definitions;
    private final String timezone;
    private final DecimalPrecisionMode decimalMode;
    private final Map<TableId, TableId> selectedTables;

    public TransformTranslator(PipelineDef definition) {
        this(definition, Map.of());
    }

    /** Keeps selectors tied to the admitted definition while events retain physical checkpoint identities. */
    public TransformTranslator(PipelineDef definition, Map<TableId, TableId> selectedTables) {
        definitions = List.copyOf(definition.getTransforms());
        this.selectedTables = Map.copyOf(selectedTables);
        timezone = definition
                .getConfig()
                .get(org.apache.flink.cdc.common.pipeline.PipelineOptions.PIPELINE_LOCAL_TIME_ZONE);
        decimalMode = definition
                .getConfig()
                .get(org.apache.flink.cdc.common.pipeline.PipelineOptions.PIPELINE_TRANSFORM_DECIMAL_PRECISION_MODE);
        for (TransformDef transform : definitions) {
            new Selectors.SelectorsBuilder()
                    .includeTables(transform.getSourceTable())
                    .build();
            if (present(transform.getPartitionKeys())
                    || present(transform.getTableOptions())
                    || present(transform.getPostTransformConverter())) {
                throw new UnsupportedOperationException(
                        "The selected Sink does not support transform partition keys, table options or post converters");
            }
        }
    }

    public Transformation<Event> translate(Transformation<Event> input, int parallelism, String definitionId) {
        if (definitions.isEmpty()) return input;
        OneInputTransformation<Event, Event> transform =
                new OneInputTransformation<>(input, "Transform", this::createOperator, Event.class, parallelism);
        transform.setUid(new OperatorUidGenerator("data-sync-transform").generateUid(definitionId));
        return transform;
    }

    public OneInputStreamOperator<Event, Event> createOperator() {
        return new TransformOperator();
    }

    /** Resolves the identical output schema during admission, execution and recovery. */
    public Schema transformSchema(TableId table, Schema source) {
        CompiledTransform transform = compile(table, source);
        return transform == null ? source : transform.output;
    }

    /** Retains physical uniqueness only for columns forwarded without a calculation or rename. */
    public DataSourceTable transformMetadata(DataSourceTable table) {
        Schema input =
                Schema.from(table.schema(), table.tableComment(), table.columnComments(), table.columnProperties());
        CompiledTransform transform = compile(table.table(), input);
        if (transform == null) return table;
        Schema schema = transform.output;
        if (transform.overridesKeys) {
            List<String> physicalKeys =
                    schema.primaryKeys().stream().map(transform.forwarded::get).toList();
            if (new HashSet<>(physicalKeys).size() != physicalKeys.size()) {
                throw new IllegalArgumentException("Transform primary keys repeat the same physical source column");
            }
            if (physicalKeys.size() != table.schema().primaryKeys().size()
                    || !new HashSet<>(physicalKeys)
                            .equals(new HashSet<>(table.schema().primaryKeys()))) {
                // Alternate keys retain the same physical uniqueness and comparison checks as Task key overrides.
                SchemaValidator.writeSchema(table, physicalKeys);
            }
        }
        return new DataSourceTable(
                table.datasourceType(),
                table.table(),
                schema.toTableSchema(),
                table.revision(),
                schema.comment(),
                schema.columnComments(),
                table.columnFeatures(),
                schema.columnProperties(),
                table.uniqueKeys().stream()
                        .filter(transform.preserved::containsAll)
                        .toList());
    }

    private TransformDef rule(TableId table) {
        for (TransformDef transform : definitions) {
            Selectors selector = new Selectors.SelectorsBuilder()
                    .includeTables(transform.getSourceTable())
                    .build();
            TableId selected = selectedTables.getOrDefault(table, table);
            if (selector.isMatch(FlinkSchemaConverter.tableId(selected))
                    || selector.isMatch(FlinkSchemaConverter.tableId(table))) return transform;
        }
        return null;
    }

    private CompiledTransform compile(TableId table, Schema source) {
        TransformDef definition = rule(table);
        if (definition == null) return null;
        String projection = present(definition.getProjection()) ? definition.getProjection() : "*";
        org.apache.flink.cdc.common.schema.Schema pre = FlinkSchemaConverter.schema(source);
        List<ProjectionColumn> projected = TransformParser.generateProjectionColumns(
                projection, pre.getColumns(), List.of(), METADATA, decimalMode);
        Set<String> preserved = projected.stream()
                .filter(column -> column.getOriginalColumnNames().size() == 1
                        && column.getExpression()
                                .equals(column.getOriginalColumnNames().getFirst())
                        && column.getColumnName()
                                .equals(column.getOriginalColumnNames().getFirst()))
                .map(ProjectionColumn::getColumnName)
                .collect(Collectors.toSet());
        Map<String, String> forwarded = new LinkedHashMap<>();
        for (ProjectionColumn column : projected) {
            if (column.getOriginalColumnNames().size() == 1
                    && column.getExpression()
                            .equals(column.getOriginalColumnNames().getFirst())) {
                forwarded.put(
                        column.getColumnName(), column.getOriginalColumnNames().getFirst());
            }
        }
        boolean overridesKeys = present(definition.getPrimaryKeys());
        List<String> keys = overridesKeys
                ? Arrays.stream(definition.getPrimaryKeys().split(",", -1))
                        .map(String::trim)
                        .toList()
                : preserved.containsAll(source.primaryKeys()) ? source.primaryKeys() : List.of();
        if (overridesKeys) {
            for (String key : keys) {
                String original = forwarded.get(key);
                if (original == null
                        || source.column(original).orElseThrow().type().isNullable()) {
                    throw new IllegalArgumentException(
                            "Transform primary keys require direct, non-null physical source columns");
                }
            }
        }
        List<PhysicalColumn> columns = new ArrayList<>();
        for (ProjectionColumn column : projected) {
            PhysicalColumn original = column.getOriginalColumnNames().size() == 1
                            && column.getExpression()
                                    .equals(column.getOriginalColumnNames().getFirst())
                    ? source.column(column.getOriginalColumnNames().getFirst()).orElse(null)
                    : null;
            columns.add(new PhysicalColumn(
                    column.getColumnName(),
                    FlinkSchemaConverter.type(column.getDataType())
                            .copy(!keys.contains(column.getColumnName())
                                    && column.getDataType().isNullable()),
                    original == null ? null : original.comment(),
                    original == null ? null : original.properties()));
        }
        Schema output = new Schema(columns, keys, source.comment());
        PostTransformChangeInfo info = PostTransformChangeInfo.of(
                FlinkSchemaConverter.tableId(table), pre, FlinkSchemaConverter.schema(output));
        TransformProjectionProcessor projector = new TransformProjectionProcessor(
                info, projection, timezone, decimalMode, List.of(), List.of(), METADATA, Map.of());
        TransformFilterProcessor filter = TransformFilter.of(definition.getFilter())
                .map(expression -> TransformFilterProcessor.of(
                        info, expression, timezone, decimalMode, List.of(), List.of(), METADATA, Map.of()))
                .orElse(null);
        return new CompiledTransform(source, output, projector, filter, preserved, forwarded, overridesKeys);
    }

    private static boolean present(String text) {
        return text != null && !text.isBlank();
    }

    private final class TransformOperator implements OneInputStreamOperator<Event, Event> {
        private final Map<TableId, Schema> sources = new LinkedHashMap<>();
        private final Map<TableId, CompiledTransform> transforms = new LinkedHashMap<>();

        @Override
        public void processElement(Event event, Collector<Event> output) throws Exception {
            if (event instanceof CreateTableEvent create) {
                sources.put(create.tableId(), create.getPhysicalSchema());
                CompiledTransform compiled = compile(create.tableId(), create.getPhysicalSchema());
                transforms.put(create.tableId(), compiled);
                output.collect(compiled == null ? create : new CreateTableEvent(create.tableId(), compiled.output));
            } else if (event instanceof SchemaChangeEvent change) {
                Schema previous = sources.get(change.tableId());
                if (previous == null) throw new IllegalStateException("Schema event precedes table metadata");
                Schema next = SchemaUtils.applySchemaChangeEvent(previous, change);
                Schema before = transformSchema(change.tableId(), previous);
                CompiledTransform compiled = compile(change.tableId(), next);
                Schema after = compiled == null ? next : compiled.output;
                sources.put(change.tableId(), next);
                transforms.put(change.tableId(), compiled);
                for (SchemaChangeEvent projected : SchemaUtils.diff(change.tableId(), before, after))
                    output.collect(projected);
            } else if (event instanceof DataChangeEvent data) {
                if (!sources.containsKey(data.tableId()))
                    throw new IllegalStateException("Data event precedes table metadata");
                CompiledTransform compiled = transforms.get(data.tableId());
                DataChangeEvent transformed = compiled == null ? data : compiled.apply(data);
                if (transformed != null) output.collect(transformed);
            } else {
                throw new UnsupportedOperationException("Unsupported transform event type");
            }
        }
    }

    private static final class CompiledTransform {
        private final Schema input;
        private final Schema output;
        private final TransformProjectionProcessor projector;
        private final TransformFilterProcessor filter;
        private final Set<String> preserved;
        private final Map<String, String> forwarded;
        private final boolean overridesKeys;

        private CompiledTransform(
                Schema input,
                Schema output,
                TransformProjectionProcessor projector,
                TransformFilterProcessor filter,
                Set<String> preserved,
                Map<String, String> forwarded,
                boolean overridesKeys) {
            this.input = input;
            this.output = output;
            this.projector = projector;
            this.filter = filter;
            this.preserved = Set.copyOf(preserved);
            this.forwarded = Map.copyOf(forwarded);
            this.overridesKeys = overridesKeys;
        }

        private DataChangeEvent apply(DataChangeEvent event) {
            TransformContext context = new TransformContext();
            context.epochTime = System.currentTimeMillis();
            context.meta = event.meta();
            RowData before = project(event.before(), event.op() == OperationType.UPDATE ? "-U" : "-D", context);
            RowData after = project(event.after(), event.op() == OperationType.INSERT ? "+I" : "+U", context);
            DataChangeEvent result;
            if (before == null && after == null) return null;
            if (event.op() == OperationType.UPDATE && (before == null || after == null)) {
                result = before == null
                        ? DataChangeEvent.insertEvent(event.tableId(), after, event.meta())
                        : DataChangeEvent.deleteEvent(event.tableId(), before, event.meta());
            } else {
                result = event.withImages(before, after);
            }
            ChangeEventId identity = event.changeEventId();
            if (identity != null && result.changeEventId() == null) {
                result = result.withChangeEventId(new ChangeEventId(
                        identity.sourceId(), identity.eventId(), 0, result.op() == OperationType.UPDATE ? 2 : 1));
            }
            return result;
        }

        private RowData project(RowData row, String operation, TransformContext context) {
            if (row == null) return null;
            if (row.getArity() != input.columns().size())
                throw new IllegalArgumentException("Transform row and schema arity differ");
            Object[] values = new Object[row.getArity()];
            for (int index = 0; index < values.length; index++) {
                Object value = row.getField(index);
                values[index] = value instanceof OffsetDateTime time ? time.toZonedDateTime() : value;
            }
            context.opType = operation;
            Object[] projected = projector.project(values, context);
            if (filter != null && !filter.test(values, projected, context)) return null;
            for (int index = 0; index < projected.length; index++) {
                Object value = projected[index];
                if (value instanceof ZonedDateTime time) projected[index] = time.toOffsetDateTime();
                if (value instanceof Instant time) projected[index] = time.atOffset(ZoneOffset.UTC);
            }
            return GenericRowData.of(projected);
        }
    }
}
