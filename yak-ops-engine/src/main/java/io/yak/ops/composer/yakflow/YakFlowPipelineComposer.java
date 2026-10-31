package io.yak.ops.composer.yakflow;

import io.yak.ops.composer.PipelineComposer;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.DynamicTableSelection;
import io.yak.ops.composer.definition.EndpointOptions;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.composer.definition.TableBinding;
import io.yak.ops.composer.definition.TableConfig;
import io.yak.ops.composer.execution.ExecutionPhase;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.composer.execution.TableExecutionProgress;
import io.yak.ops.composer.validation.PipelineDefinitionValidator;
import io.yak.ops.composer.validation.SchemaPlanner;
import io.yak.ops.composer.validation.SchemaValidator;
import io.yak.ops.composer.yakflow.translator.DataSinkTranslator;
import io.yak.ops.composer.yakflow.translator.DataSourceTranslator;
import io.yak.ops.composer.yakflow.translator.PartitioningTranslator;
import io.yak.ops.composer.yakflow.translator.SchemaTranslator;
import io.yak.ops.composer.yakflow.translator.TransformTranslator;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.SinkFactoryContext;
import io.yak.ops.connector.base.factory.SourceFactoryContext;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.core.factories.DataSinkFactory;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Composes one Event graph using connector factories and the Flink CDC translator stages. */
public final class YakFlowPipelineComposer implements PipelineComposer {
    private final DataSourceLookupService resources;
    private final FactoryUtil factories;
    private final ReadonlyConfig options;
    private final ExecutionConfigurationFactory configurationFactory;
    private final Map<String, ReadonlyConfig> sourceConnectorOptions;
    private final Map<String, ReadonlyConfig> sinkConnectorOptions;
    private final PipelineExecutor executor;
    private final IncrementalBatchPreparation incrementalPreparation;

    public YakFlowPipelineComposer(
            DataSourceLookupService resources,
            FactoryUtil factories,
            ReadonlyConfig options,
            ExecutionConfigurationFactory configurationFactory,
            Map<String, ReadonlyConfig> sourceConnectorOptions,
            Map<String, ReadonlyConfig> sinkConnectorOptions,
            PipelineExecutor executor,
            IncrementalBatchPreparation incrementalPreparation) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.options = Objects.requireNonNull(options, "options");
        this.configurationFactory = Objects.requireNonNull(configurationFactory, "configurationFactory");
        this.sourceConnectorOptions = Map.copyOf(sourceConnectorOptions);
        this.sinkConnectorOptions = Map.copyOf(sinkConnectorOptions);
        this.executor = Objects.requireNonNull(executor, "executor");
        this.incrementalPreparation = Objects.requireNonNull(incrementalPreparation, "incrementalPreparation");
    }

    @Override
    public PipelinePlan plan(PipelineDef definition, PipelineContext context) {
        PipelineOptions inputs = PipelineDefinitionValidator.validateConfig(definition, factories);
        effectiveEnvironment(definition);
        return resolve(definition, inputs, context.workspaceId(), context.instanceId());
    }

    @Override
    public PipelineExecution compose(PipelineDef definition, PipelinePlan plan, PipelineContext context) {
        return new YakFlowPipelineExecution(definition, plan, context, this, executor, incrementalPreparation);
    }

    Configuration configuration(PipelineDef definition, TaskExecutionIdentity identity, boolean debug) {
        PipelineOptions inputs = PipelineOptions.from(definition);
        Boundedness boundedness = factories
                .source(inputs.sourceIdentifier())
                .boundedness(EndpointOptions.connectorOptions(
                        inputs.tables().getFirst().source()));
        Configuration configuration = configurationFactory.create(
                identity, boundedness, debug, inputs.schemaChangesEnabled(), inputs.allowDropColumns());
        String name = definition.getConfig().toMap().get("name");
        if (name != null) configuration.set(io.yak.ops.core.configuration.PipelineOptions.NAME, name);
        return configuration;
    }

    /** Constructs and validates the source before target DDL, without starting readers or an enumerator. */
    SourceTransformation<Event> translateSource(
            PipelineDef definition,
            PipelinePlan plan,
            TaskExecutionIdentity identity,
            TableExecutionProgress progress,
            Configuration configuration) {
        if (plan.tables().isEmpty()) throw new IllegalArgumentException("A source requires admitted tables");
        PipelineOptions inputs = PipelineOptions.from(definition);
        DataSourceFactory sourceFactory =
                factories.source(definition.getSource().getType());
        TableBinding first = plan.tables().getFirst();
        int sourceParallelism = effectiveEnvironment(definition).get(EnvironmentOptions.SOURCE_PARALLELISM);
        ReadonlyConfig sourceConnection = resources.connectionOptions(
                identity.workspaceId(),
                inputs.sourceDataSourceId(),
                first.source().revision());
        DataSourceInfo sourceInfo = resources.dataSource(identity.workspaceId(), inputs.sourceDataSourceId());
        if (sourceInfo.revision() != first.source().revision()) {
            throw new IllegalStateException("Source datasource changed before connector creation");
        }
        TableMetrics metrics = progress.metrics();
        List<TableSourceFactoryContext> sources = new ArrayList<>();
        Map<TableId, Schema> schemas = new LinkedHashMap<>();
        for (TableBinding table : plan.tables()) {
            metrics.register(table.source().table());
            ReadonlyConfig sourceOptions = creationOptions(
                    sourceConnectorOptions, sourceFactory.identifier(), table.sourceOptions(), sourceConnection);
            if (inputs.dynamicSelection() != null
                    && sourceInfo.database() != null
                    && !sourceInfo.database().isBlank()) {
                Map<String, Object> scoped = new LinkedHashMap<>(sourceOptions.getSourceMap());
                scoped.put(
                        MySqlSourceOptions.DATABASE_NAME.key(), java.util.regex.Pattern.quote(sourceInfo.database()));
                sourceOptions = ReadonlyConfig.fromMap(scoped);
            }
            sources.add(new TableSourceFactoryContext(
                    sourceOptions, table.source().table(), table.source().schema(), metrics));
            schemas.put(table.source().table(), physicalSchema(table.source()));
        }
        DataSource source = sourceFactory.createDataSource(new SourceFactoryContext(
                sources, configuration, Thread.currentThread().getContextClassLoader(), schemas));
        SourceTransformation<Event> transformation =
                new DataSourceTranslator().translate(source, sourceParallelism, identity.definitionId());
        definition.getSource().getName().ifPresent(transformation::setName);
        boolean bounded = transformation.getSource().getBoundedness() == Boundedness.BOUNDED;
        if (bounded != inputs.bounded())
            throw new IllegalArgumentException("Created Source boundedness conflicts with Pipeline mode");
        return transformation;
    }

    StreamGraph translate(
            PipelineDef definition,
            PipelinePlan plan,
            TaskExecutionIdentity identity,
            TableExecutionProgress progress,
            Configuration configuration,
            SourceTransformation<Event> source) {
        if (plan.tables().isEmpty() || plan.tables().stream().anyMatch(table -> table.targetSchema() == null)) {
            throw new IllegalStateException("Every target must be prepared and read back before composition");
        }
        PipelineOptions inputs = PipelineOptions.from(definition);
        DataSinkFactory sinkFactory = factories.sink(definition.getSink().getType());
        TableBinding first = plan.tables().getFirst();
        int sourceParallelism = source.getParallelism();
        int sinkParallelism = effectiveEnvironment(definition).get(EnvironmentOptions.SINK_PARALLELISM);
        ReadonlyConfig sinkConnection = resources.connectionOptions(
                identity.workspaceId(),
                inputs.targetDataSourceId(),
                first.targetDatasource().revision());
        TableMetrics metrics = progress.metrics();
        List<TableSinkFactoryContext> sinks = new ArrayList<>();
        Map<TableId, Schema> schemas = new LinkedHashMap<>();
        Map<TableId, TableId> selectedTables = new LinkedHashMap<>();
        for (TableBinding table : plan.tables()) {
            schemas.put(table.source().table(), physicalSchema(table.source()));
            selectedTables.put(table.source().table(), EndpointOptions.selectedTable(table.sourceParameters()));
            sinks.add(new TableSinkFactoryContext(
                    creationOptions(
                            sinkConnectorOptions,
                            sinkFactory.identifier(),
                            EndpointOptions.connectorOptions(table.sinkParameters()),
                            sinkConnection),
                    table.source().table(),
                    table.writeSourceSchema(),
                    table.targetTable(),
                    table.targetSchema(),
                    metrics));
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        TransformTranslator transforms = new TransformTranslator(definition, selectedTables);
        boolean sourceProducesChangelog = factories
                .source(definition.getSource().getType())
                .producesChangelog(EndpointOptions.connectorOptions(first.sourceParameters()));
        DataSink sink;
        DynamicTableSink dynamicSink;
        if (inputs.dynamicSelection() == null) {
            dynamicSink = null;
            sink = sinkFactory.createDataSink(new SinkFactoryContext(
                    sinks, configuration, classLoader, transforms::transformSchema, schemas, sourceProducesChangelog));
        } else {
            DynamicTableSelection selection = inputs.dynamicSelection();
            boolean sameServer = "MYSQL".equals(first.targetDatasource().type())
                    && resources.samePhysicalServer(
                            identity.workspaceId(), inputs.sourceDataSourceId(), inputs.targetDataSourceId());
            Map<TableId, DataSink> initialSinks = new LinkedHashMap<>();
            for (TableSinkFactoryContext table : sinks) {
                initialSinks.put(
                        table.sourceTable(),
                        sinkFactory.createDataSink(new SinkFactoryContext(
                                List.of(table),
                                configuration,
                                classLoader,
                                transforms::transformSchema,
                                Map.of(table.sourceTable(), schemas.get(table.sourceTable())),
                                true)));
            }
            java.util.function.Function<TableId, TableId> naming = table -> {
                TableId selected = selection.target(table);
                TableId target =
                        first.targetDatasource().resolveTable(selected.catalog(), selected.schema(), selected.table());
                SchemaValidator.validateNamespace(first.targetDatasource(), target);
                if (sameServer && selection.matches(target)) {
                    throw new IllegalArgumentException(
                            "Dynamic target would be captured again by the source selection");
                }
                return target;
            };
            dynamicSink = new DynamicTableSink(
                    initialSinks,
                    naming,
                    (table, raw, restored) -> discoveredSink(
                            selection,
                            naming.apply(table),
                            table,
                            raw,
                            restored,
                            inputs,
                            first,
                            identity,
                            sinkFactory,
                            sinkConnection,
                            metrics,
                            configuration,
                            classLoader,
                            transforms));
            sink = dynamicSink;
        }
        Transformation<Event> events = transforms.translate(source, sourceParallelism, identity.definitionId());
        events = new SchemaTranslator()
                .translate(
                        events,
                        plan.tables(),
                        sourceParallelism,
                        identity.definitionId(),
                        definition.getConfig().toMap().get("schema.operator.uid"),
                        dynamicSink == null ? null : dynamicSink::target);
        events = new PartitioningTranslator()
                .translate(events, sink, sourceParallelism, sinkParallelism, identity.definitionId());
        io.yak.ops.flow.runtime.transformations.SinkTransformation<Event> output =
                new DataSinkTranslator().translate(events, sink, sinkParallelism, identity.definitionId());
        definition.getSink().getName().ifPresent(output::setName);
        return new StreamGraphGenerator(output, configuration).generate();
    }

    private DynamicTableSink.Registration discoveredSink(
            DynamicTableSelection selection,
            TableId target,
            TableId source,
            Schema raw,
            SchemaVersionedTableState restored,
            PipelineOptions inputs,
            TableBinding initial,
            TaskExecutionIdentity identity,
            DataSinkFactory sinkFactory,
            ReadonlyConfig sinkConnection,
            TableMetrics metrics,
            Configuration configuration,
            ClassLoader loader,
            TransformTranslator transforms) {
        DataSourceInfo targetInfo = initial.targetDatasource();
        if (restored == null) {
            DataSourceInfo current = resources.dataSource(identity.workspaceId(), inputs.targetDataSourceId());
            DataSourceInfo sourceInfo = resources.dataSource(identity.workspaceId(), inputs.sourceDataSourceId());
            if (current.revision() != targetInfo.revision()
                    || sourceInfo.revision() != initial.source().revision()) {
                throw new IllegalStateException("Datasource revision changed after dynamic task admission");
            }
            sourceInfo.resolveTable(source.catalog(), source.schema(), source.table());
        } else if (!target.equals(restored.targetTable())) {
            throw new IllegalArgumentException("Checkpointed target differs from the published naming rule");
        }
        DataSourceTable sourceMetadata = new DataSourceTable(
                initial.source().datasourceType(),
                source,
                raw.toTableSchema(),
                initial.source().revision(),
                raw.comment(),
                raw.columnComments(),
                raw.columnProperties().values().stream().anyMatch(properties -> !properties.isKnown())
                        ? Set.of("UNKNOWN_GENERATION")
                        : Set.of(),
                raw.columnProperties(),
                List.of(raw.primaryKeys()));
        DataSourceTable projected = transforms.transformMetadata(sourceMetadata);
        TableSchema write = SchemaValidator.writeSchema(projected, null);
        ReadonlyConfig sinkOptions = creationOptions(
                sinkConnectorOptions,
                sinkFactory.identifier(),
                ReadonlyConfig.fromMap(selection.sinkDefaults()),
                sinkConnection);
        Schema before;
        Schema after;
        List<SchemaChangeEvent> changes;
        if (restored == null) {
            CreateTableEvent desired = SchemaPlanner.createEvent(projected, targetInfo, target);
            DataSourceTable existing = resources
                    .findTable(
                            identity.workspaceId(),
                            inputs.targetDataSourceId(),
                            target.catalog(),
                            target.schema(),
                            target.table())
                    .orElse(null);
            if (existing != null && existing.revision() != targetInfo.revision()) {
                throw new IllegalStateException("Target revision changed during dynamic table planning");
            }
            SchemaChangePlan plan = SchemaPlanner.plan(desired, targetInfo.type(), existing);
            SchemaValidator.requireCompatible(plan);
            changes = plan.changes();
            before = existing == null
                    ? desired.getPhysicalSchema()
                    : Schema.from(existing.schema(), null, Map.of(), Map.of());
            after = before;
            for (SchemaChangeEvent change : changes) after = SchemaUtils.applySchemaChangeEvent(after, change);
        } else {
            before = restored.targetSchema();
            after = before;
            changes = List.of();
        }
        sinkFactory.validateSchema(sinkOptions, write, after.toTableSchema(), true);
        metrics.register(source);
        DataSink sink = sinkFactory.createDataSink(new SinkFactoryContext(
                List.of(new TableSinkFactoryContext(
                        sinkOptions, source, write, target, after.toTableSchema(), metrics)),
                configuration,
                loader,
                transforms::transformSchema,
                Map.of(source, raw),
                true));
        String eventId = new io.yak.ops.connector.cdc.mysql.source.events.TableDiscoveryEvent(source, raw).eventId();
        SchemaChangeRequest request = restored == null
                ? new SchemaChangeRequest(eventId, source, target, raw, raw, before, after, changes, true)
                : null;
        return new DynamicTableSink.Registration(sink, request);
    }

    private ReadonlyConfig effectiveEnvironment(PipelineDef definition) {
        return EnvironmentOptions.withParallelism(
                options, definition.getConfig().toMap().get("parallelism"));
    }

    public MetadataApplier createMetadataApplier(TableBinding table, TaskExecutionIdentity identity) {
        DataSinkFactory factory = factories.sink(table.sinkParameters().get(EndpointOptions.TYPE));
        ReadonlyConfig connection = resources.connectionOptions(
                identity.workspaceId(),
                table.sinkParameters().get(EndpointOptions.DATASOURCE_ID),
                table.targetDatasource().revision());
        return factory.createMetadataApplier(creationOptions(
                sinkConnectorOptions,
                factory.identifier(),
                EndpointOptions.connectorOptions(table.sinkParameters()),
                connection));
    }

    public TableBinding preparedTarget(TableBinding table, TaskExecutionIdentity identity) {
        DataSourceTable actual = resources.table(
                identity.workspaceId(),
                table.sinkParameters().get(EndpointOptions.DATASOURCE_ID),
                table.targetTable().catalog(),
                table.targetTable().schema(),
                table.targetTable().table());
        SchemaValidator.validatePrepared(
                table.schemaChange(), table.targetDatasource().type(), actual);
        return table.withTarget(actual);
    }

    private static Schema physicalSchema(DataSourceTable table) {
        return Schema.from(table.schema(), table.tableComment(), table.columnComments(), table.columnProperties());
    }

    private static ReadonlyConfig creationOptions(
            Map<String, ReadonlyConfig> configured,
            String identifier,
            ReadonlyConfig operation,
            ReadonlyConfig connection) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (configured.containsKey(identifier))
            values.putAll(configured.get(identifier).getSourceMap());
        values.putAll(operation.getSourceMap());
        values.putAll(connection.getSourceMap());
        return ReadonlyConfig.fromMap(values);
    }

    private PipelinePlan resolve(PipelineDef definition, PipelineOptions job, String workspaceId, String instanceId) {
        boolean prepareSchema = job.steps().contains(ExecutionPhase.SCHEMA);
        List<TableBinding> tables = new ArrayList<>();
        List<ExecutionResource> claims = new ArrayList<>();
        Set<TableId> sources = new HashSet<>();
        Set<TableId> targets = new HashSet<>();
        StringBuilder bindings = new StringBuilder()
                .append(encodePart(definition.getSource().getType()))
                .append(encodePart(definition.getSink().getType()))
                .append(encodePart(
                        new TreeMap<>(definition.getSource().getConfig().toMap()).toString()))
                .append(encodePart(
                        new TreeMap<>(definition.getSink().getConfig().toMap()).toString()));
        for (TableConfig mapping : job.tables()) {
            ReadonlyConfig source = mapping.source();
            ReadonlyConfig sink = mapping.sink();
            String sourceId = source.get(EndpointOptions.DATASOURCE_ID);
            String sinkId = sink.get(EndpointOptions.DATASOURCE_ID);
            DataSourceFactory sourceFactory = factories.source(source.get(EndpointOptions.TYPE));
            DataSinkFactory sinkFactory = factories.sink(sink.get(EndpointOptions.TYPE));
            Supplier<DataSourceTable> sourceLookup = () -> resources.table(
                    workspaceId,
                    sourceId,
                    source.get(EndpointOptions.DATABASE),
                    source.get(EndpointOptions.SCHEMA),
                    source.get(EndpointOptions.TABLE));
            DataSourceTable from = prepareSchema ? SchemaValidator.sourceMetadata(sourceLookup) : sourceLookup.get();
            DataSourceInfo targetInfo;
            TableId targetTable;
            DataSourceTable actual;
            if (prepareSchema) {
                targetInfo = resources.dataSource(workspaceId, sinkId);
                targetTable = targetInfo.resolveTable(
                        sink.get(EndpointOptions.DATABASE),
                        sink.get(EndpointOptions.SCHEMA),
                        sink.get(EndpointOptions.TABLE));
                SchemaValidator.validateNamespace(targetInfo, targetTable);
                actual = resources
                        .findTable(
                                workspaceId, sinkId, targetTable.catalog(), targetTable.schema(), targetTable.table())
                        .orElse(null);
                if (actual != null
                        && (actual.revision() != targetInfo.revision()
                                || !actual.table().equals(targetTable))) {
                    throw new IllegalStateException("Target datasource binding changed during planning");
                }
            } else {
                actual = resources.table(
                        workspaceId,
                        sinkId,
                        sink.get(EndpointOptions.DATABASE),
                        sink.get(EndpointOptions.SCHEMA),
                        sink.get(EndpointOptions.TABLE));
                targetTable = actual.table();
                targetInfo = new DataSourceInfo(
                        actual.datasourceType(), targetTable.catalog(), targetTable.schema(), actual.revision());
            }
            sourceFactory.validateDatasource(from.datasourceType());
            sourceFactory.validateSchema(EndpointOptions.connectorOptions(source), from.schema());
            sinkFactory.validateDatasource(targetInfo.type());
            PipelineDefinitionValidator.validateCapabilities(job, sourceFactory, sinkFactory);
            if (!sources.add(from.table()) || !targets.add(targetTable)) {
                throw new IllegalArgumentException("Duplicate resolved source or destination table");
            }
            if (!tables.isEmpty()
                    && (from.revision() != tables.getFirst().source().revision()
                            || targetInfo.revision()
                                    != tables.getFirst().targetDatasource().revision())) {
                throw new IllegalStateException("Datasource revision changed while planning the table collection");
            }
            if (mapping.targetPrimaryKeys() != null && !"MYSQL".equals(targetInfo.type())) {
                throw new IllegalArgumentException("Explicit target primary keys currently require a MySQL target");
            }
            SchemaValidator.validateCdcWrite(
                    from.schema(),
                    targetInfo.type(),
                    source,
                    sink,
                    mapping.targetPrimaryKeys(),
                    sourceFactory.producesChangelog(EndpointOptions.connectorOptions(source)),
                    job.schemaChangesEnabled());
            TransformTranslator transforms =
                    new TransformTranslator(definition, Map.of(from.table(), EndpointOptions.selectedTable(source)));
            if (sink.get(JdbcSinkOptions.WRITE_MODE) == JdbcWriteMode.RETRACT) {
                transforms.validateRetraction(from.table());
            }
            DataSourceTable projected = transforms.transformMetadata(from);
            TableSchema writeSource = SchemaValidator.writeSchema(projected, mapping.targetPrimaryKeys());
            CreateTableEvent event = prepareSchema
                    ? SchemaPlanner.createEvent(projected, targetInfo, targetTable, mapping.targetPrimaryKeys())
                    : null;
            SchemaChangePlan schema = event == null ? null : SchemaPlanner.plan(event, targetInfo.type(), actual);
            if (schema != null) SchemaValidator.requireCompatible(schema);
            TableSchema expected = event == null ? actual.schema() : event.getSchema();
            sinkFactory.validateSchema(
                    sink,
                    writeSource,
                    expected,
                    sourceFactory.producesChangelog(EndpointOptions.connectorOptions(source)));
            if (actual != null && (schema == null || !schema.requiresDdl())) {
                sinkFactory.validateSchema(
                        sink,
                        writeSource,
                        actual.schema(),
                        sourceFactory.producesChangelog(EndpointOptions.connectorOptions(source)));
            }
            String target = encodePart(sinkId) + encodeTable(targetTable);
            String binding = encodePart(workspaceId)
                    + encodePart(sourceId)
                    + encodePart(Long.toString(from.revision()))
                    + encodeTable(from.table())
                    + encodeSchema(from.schema())
                    + encodeProperties(from.columnProperties())
                    + encodePart(sinkId)
                    + encodePart(Long.toString(targetInfo.revision()))
                    + encodeTable(targetTable)
                    + encodeSchema(expected);
            if (mapping.targetPrimaryKeys() != null)
                binding += encodePart("targetPrimaryKeys") + encodePart(String.join(",", mapping.targetPrimaryKeys()));
            binding += encodePart(
                    new TreeMap<>(EndpointOptions.connectorOptions(source).getSourceMap()).toString());
            binding += encodePart(
                    new TreeMap<>(EndpointOptions.connectorOptions(sink).getSourceMap()).toString());
            binding += encodeSchema(writeSource);
            binding += encodePart(definition.getTransforms().toString());
            binding += encodePart(new TreeMap<>(definition.getConfig().toMap()).toString());
            if (sink.get(JdbcSinkOptions.XA_ENABLED)) binding += encodePart("XA");
            if (event != null) {
                binding += encodePart("SCHEMA") + encodePart(event.getTableComment());
                binding += encodeProperties(event.getColumnProperties());
                for (Map.Entry<String, String> comment : new TreeMap<>(event.getColumnComments()).entrySet()) {
                    binding += encodePart(comment.getKey()) + encodePart(comment.getValue());
                }
            }
            if (job.dynamicSelection() == null) claims.add(new ExecutionResource("DATA_SYNC_TARGET", target));
            ReadonlyConfig prepared =
                    sourceFactory.prepareExecutionOptions(EndpointOptions.connectorOptions(source), instanceId);
            if (tables.isEmpty() && instanceId != null) {
                sourceFactory
                        .exclusiveResources(instanceId)
                        .forEach((type, key) -> claims.add(new ExecutionResource(type, key)));
            }
            Map<String, Object> preparedSink = new LinkedHashMap<>(sink.getSourceMap());
            if (sink.get(JdbcSinkOptions.XA_ENABLED) && instanceId != null) {
                preparedSink.put(JdbcSinkOptions.XA_INSTANCE_ID.key(), instanceId);
            }
            bindings.append(binding);
            tables.add(new TableBinding(
                    source,
                    ReadonlyConfig.fromMap(preparedSink),
                    from,
                    targetInfo,
                    targetTable,
                    actual == null ? null : actual.schema(),
                    event,
                    prepared,
                    schema,
                    actual,
                    writeSource,
                    transforms.semanticRevision(from.table())));
        }
        if (job.sourceDataSourceId().equals(job.targetDataSourceId())
                && sources.stream().anyMatch(targets::contains)) {
            throw new IllegalArgumentException("Destinations overlap their source tables");
        }
        if (job.dynamicSelection() != null) {
            claims.add(new ExecutionResource("DATA_SYNC_TARGET_SCOPE", encodePart(job.targetDataSourceId())));
            boolean sameServer = "MYSQL"
                            .equals(tables.getFirst().targetDatasource().type())
                    && resources.samePhysicalServer(workspaceId, job.sourceDataSourceId(), job.targetDataSourceId());
            for (TableBinding table : tables) {
                TableId mapped = job.dynamicSelection().target(table.source().table());
                TableId defaultTarget =
                        table.targetDatasource().resolveTable(mapped.catalog(), mapped.schema(), mapped.table());
                SchemaValidator.validateNamespace(table.targetDatasource(), defaultTarget);
                if (sameServer
                        && (job.dynamicSelection().matches(table.targetTable())
                                || job.dynamicSelection().matches(defaultTarget))) {
                    throw new IllegalArgumentException(
                            "Dynamic target would be captured again by the source selection");
                }
            }
        }
        TaskExecutionResources admission = new TaskExecutionResources(claims, sha256(bindings.toString()));
        return new PipelinePlan(
                admission.exclusiveResources().stream()
                        .map(resource -> new PipelinePlan.ResourceClaim(resource.type(), resource.key()))
                        .toList(),
                admission.resourceRevision(),
                tables);
    }

    private static String encodeTable(TableId table) {
        return encodePart(table.catalog()) + encodePart(table.schema()) + encodePart(table.table());
    }

    /** Physical metadata changes invalidate previously admitted schema plans. */
    private static String encodeProperties(java.util.Map<String, ColumnProperties> properties) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, ColumnProperties> entry : new TreeMap<>(properties).entrySet()) {
            ColumnProperties attribute = entry.getValue();
            value.append(encodePart(entry.getKey()))
                    .append(encodePart(attribute.defaultExpression()))
                    .append(encodePart(attribute.onUpdateExpression()))
                    .append(encodePart(attribute.nativeType()))
                    .append(encodePart(String.valueOf(attribute.defaultGenerated())))
                    .append(encodePart(String.valueOf(attribute.autoIncrement())))
                    .append(encodePart(String.valueOf(attribute.generated())));
        }
        return value.toString();
    }

    private static String encodeSchema(TableSchema schema) {
        StringBuilder value = new StringBuilder().append(schema.columnCount()).append(':');
        for (Column column : schema.columns()) {
            value.append(encodePart(column.name()))
                    .append(encodePart(column.dataType().asSerializableString()));
        }
        value.append(schema.primaryKeys().size()).append(':');
        for (String key : schema.primaryKeys()) value.append(encodePart(key));
        return value.toString();
    }

    private static String encodePart(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }
}
