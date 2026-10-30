package io.yak.ops.sync.runtime.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.sync.runtime.config.PipelineExecutionPlan;
import io.yak.ops.sync.runtime.config.JobOptions;
import io.yak.ops.sync.runtime.solution.SolutionValidator;
import io.yak.ops.sync.runtime.solution.Step;
import io.yak.ops.sync.runtime.validation.PipelineDefinitionValidator;
import io.yak.ops.sync.runtime.validation.SchemaPlanner;
import io.yak.ops.sync.runtime.validation.SchemaValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves authorized DATA_SYNC metadata and constructs logical Job and admission plans. */
public final class JobPlanner {
    private final DataSourceLookupService resources;
    private final FactoryUtil factories;

    public JobPlanner(DataSourceLookupService resources, FactoryUtil factories) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    /**
     * Declares every exclusive destination and its non-secret binding fingerprint.
     *
     * @param parameters pinned DATA_SYNC definition
     * @param context trusted Task execution identity
     * @return complete admission claims validated against the expected schema
     */
    public TaskExecutionResources admissionPlan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
        return plan(parameters, context).admissionPlan();
    }

    /**
     * Resolves source and target metadata without DDL, connection credentials or Job submission.
     *
     * @param parameters pinned DATA_SYNC definition
     * @param context trusted Task execution identity
     * @return immutable logical plan; missing targets require explicit SCHEMA preparation
     */
    public JobPlan plan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
        return resolve(PipelineDefinitionValidator.validateConfig(parameters, factories), context.workspaceId(), context.instanceId());
    }

    public TaskExecutionResources admissionPlan(PipelineDef definition, TaskExecutionIdentity context) {
        return plan(definition, context).admissionPlan();
    }

    public JobPlan plan(PipelineDef definition, TaskExecutionIdentity context) {
        return resolve(PipelineDefinitionValidator.validateConfig(definition, factories), context.workspaceId(), context.instanceId());
    }

    /**
     * Reads and verifies a prepared target using its originally admitted binding.
     *
     * @param plan admitted table route
     * @param context trusted Task execution identity
     * @return the same route with actual physical target metadata
     */
    public TablePlan preparedTarget(TablePlan plan, TaskExecutionIdentity context) {
        TableId table = plan.targetTable();
        DataSourceTable actual = resources.table(
                context.workspaceId(),
                plan.sinkParameters().get(JobOptions.DATASOURCE_ID),
                table.catalog(),
                table.schema(),
                table.table());
        SchemaValidator.validatePrepared(
                plan.schemaChange(), plan.targetDatasource().type(), actual);
        return plan.withTarget(actual);
    }

    private JobPlan resolve(PipelineExecutionPlan job, String workspaceId, String instanceId) {
        boolean prepareSchema = job.steps().contains(Step.SCHEMA);
        List<TablePlan> tables = new ArrayList<>();
        List<ExecutionResource> claims = new ArrayList<>();
        java.util.Set<TableId> sources = new java.util.HashSet<>();
        java.util.Set<TableId> targets = new java.util.HashSet<>();
        StringBuilder bindings = new StringBuilder();
        for (io.yak.ops.sync.runtime.config.TableConfig mapping : job.tables()) {
            ReadonlyConfig source = mapping.source();
            ReadonlyConfig sink = mapping.sink();
            String sourceId = source.get(JobOptions.DATASOURCE_ID);
            String sinkId = sink.get(JobOptions.DATASOURCE_ID);
            io.yak.ops.connector.base.factory.TableSourceFactory sourceFactory =
                    factories.source(source.get(JobOptions.TYPE));
            io.yak.ops.connector.base.factory.TableSinkFactory sinkFactory = factories.sink(sink.get(JobOptions.TYPE));
            java.util.function.Supplier<DataSourceTable> sourceLookup = () -> resources.table(
                    workspaceId,
                    sourceId,
                    source.get(JobOptions.DATABASE),
                    source.get(JobOptions.SCHEMA),
                    source.get(JobOptions.TABLE));
            DataSourceTable from = prepareSchema ? SchemaValidator.sourceMetadata(sourceLookup) : sourceLookup.get();
            DataSourceInfo targetInfo;
            TableId targetTable;
            DataSourceTable actual;
            if (prepareSchema) {
                targetInfo = resources.dataSource(workspaceId, sinkId);
                targetTable = targetInfo.resolveTable(
                        sink.get(JobOptions.DATABASE), sink.get(JobOptions.SCHEMA), sink.get(JobOptions.TABLE));
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
                        sink.get(JobOptions.DATABASE),
                        sink.get(JobOptions.SCHEMA),
                        sink.get(JobOptions.TABLE));
                targetTable = actual.table();
                targetInfo = new DataSourceInfo(
                        actual.datasourceType(), targetTable.catalog(), targetTable.schema(), actual.revision());
            }
            sourceFactory.validateDatasource(from.datasourceType());
            sourceFactory.validateSchema(JobOptions.connectorOptions(source), from.schema());
            SolutionValidator.validateSelection(
                    factories,
                    job.solutionType(),
                    from.datasourceType(),
                    targetInfo.type(),
                    job.steps(),
                    job.schemaChangesEnabled(),
                    sink.get(JdbcSinkOptions.WRITE_MODE).name());
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
                    job.schemaChangesEnabled());
            TableSchema writeSource = SchemaValidator.writeSchema(from, mapping.targetPrimaryKeys());
            CreateTableEvent event = prepareSchema
                    ? SchemaPlanner.createEvent(from, targetInfo, targetTable, mapping.targetPrimaryKeys())
                    : null;
            SchemaChangePlan schema = event == null ? null : SchemaPlanner.plan(event, targetInfo.type(), actual);
            if (schema != null) SchemaValidator.requireCompatible(schema);
            TableSchema expected = event == null ? actual.schema() : event.getSchema();
            sinkFactory.validateSchema(sink, writeSource, expected, sourceFactory.producesChangelog());
            if (actual != null && (schema == null || !schema.requiresDdl())) {
                sinkFactory.validateSchema(sink, writeSource, actual.schema(), sourceFactory.producesChangelog());
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
            if ("MYSQL_CDC".equals(sourceFactory.factoryIdentifier())) {
                String chunkKey = source.get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN);
                if (chunkKey != null) binding += encodePart("chunkKey") + encodePart(chunkKey);
                if (from.schema().primaryKeys().isEmpty()) binding += encodePart("noPhysicalPrimaryKey");
            }
            if (sink.get(JdbcSinkOptions.XA_ENABLED)) binding += encodePart("XA");
            if (event != null) {
                binding += encodePart("SCHEMA") + encodePart(event.getTableComment());
                binding += encodeProperties(event.getColumnProperties());
                for (Map.Entry<String, String> comment :
                        new java.util.TreeMap<>(event.getColumnComments()).entrySet()) {
                    binding += encodePart(comment.getKey()) + encodePart(comment.getValue());
                }
            }
            claims.add(new ExecutionResource("DATA_SYNC_TARGET", target));
            Map<String, Object> prepared =
                    new LinkedHashMap<>(JobOptions.connectorOptions(source).getSourceMap());
            if (instanceId != null && "MYSQL_CDC".equals(sourceFactory.factoryIdentifier())) {
                prepared.put(
                        MySqlSourceOptions.SERVER_ID.key(),
                        Integer.toString(MySqlCdcReplicationIdentity.serverId(instanceId)));
                prepared.put(
                        MySqlSourceOptions.TOPIC_PREFIX.key(), MySqlCdcReplicationIdentity.topicPrefix(instanceId));
                if (tables.isEmpty()) claims.add(MySqlCdcReplicationIdentity.exclusiveResource(instanceId));
            }
            Map<String, Object> preparedSink = new LinkedHashMap<>(sink.getSourceMap());
            if (sink.get(JdbcSinkOptions.XA_ENABLED) && instanceId != null) {
                preparedSink.put(JdbcSinkOptions.XA_INSTANCE_ID.key(), instanceId);
            }
            bindings.append(binding);
            tables.add(new TablePlan(
                    source,
                    ReadonlyConfig.fromMap(preparedSink),
                    from,
                    targetInfo,
                    targetTable,
                    actual == null ? null : actual.schema(),
                    event,
                    ReadonlyConfig.fromMap(prepared),
                    schema,
                    actual,
                    writeSource));
        }
        if (job.sourceDataSourceId().equals(job.targetDataSourceId())
                && sources.stream().anyMatch(targets::contains)) {
            throw new IllegalArgumentException("Destinations overlap their source tables");
        }
        return new JobPlan(tables, new TaskExecutionResources(claims, sha256(bindings.toString())));
    }

    private static String encodeTable(TableId table) {
        return encodePart(table.catalog()) + encodePart(table.schema()) + encodePart(table.table());
    }

    /** Physical metadata changes invalidate previously admitted schema plans. */
    private static String encodeProperties(java.util.Map<String, io.yak.ops.core.event.ColumnProperties> properties) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, io.yak.ops.core.event.ColumnProperties> entry :
                new java.util.TreeMap<>(properties).entrySet()) {
            io.yak.ops.core.event.ColumnProperties attribute = entry.getValue();
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
