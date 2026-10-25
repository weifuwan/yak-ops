package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
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
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionCatalog;
import io.yak.ops.plugin.task.datasync.solution.Step;
import io.yak.ops.plugin.task.datasync.validation.JobConfigValidator;
import io.yak.ops.plugin.task.datasync.validation.SchemaValidator;
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
    public TaskAdmissionPlan admissionPlan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
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
        return resolve(parameters, context.workspaceId(), context.instanceId(), false);
    }

    /**
     * Previews the same validated schema operations used by execution without reserving resources.
     *
     * @param parameters validated product configuration
     * @param workspaceId authorized Workspace
     * @return ordered table DDL previews without connection settings
     */
    public List<SchemaPreview> preview(ReadonlyConfig parameters, String workspaceId) {
        if (!JobConfig.from(parameters).steps().contains(Step.SCHEMA)) {
            throw new IllegalArgumentException("DDL preview requires the SCHEMA step");
        }
        JobPlan plan = resolve(parameters, workspaceId, null, true);
        List<SchemaPreview> results = new ArrayList<>();
        for (int index = 0; index < plan.tables().size(); index++) {
            TablePlan table = plan.tables().get(index);
            SchemaChangePlan schema = table.schemaPlan();
            List<SchemaPreview.Difference> differences = schema.differences().stream()
                    .map(difference -> new SchemaPreview.Difference(
                            difference.columnName(),
                            difference.kind().name(),
                            difference.reason().name(),
                            difference.sourceType() == null
                                    ? null
                                    : difference.sourceType().asSerializableString(),
                            difference.targetType() == null
                                    ? null
                                    : difference.targetType().asSerializableString()))
                    .toList();
            results.add(new SchemaPreview(
                    index,
                    table.targetTable(),
                    schema.blocked()
                            ? "BLOCKED"
                            : table.targetSchema() == null ? "CREATE" : schema.requiresDdl() ? "ALTER" : "VERIFY",
                    schema.blocked()
                            ? List.of()
                            : SchemaValidator.statements(
                                    table.targetDatasource().type(), schema, table.targetMetadata()),
                    differences));
        }
        return List.copyOf(results);
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

    private JobPlan resolve(ReadonlyConfig parameters, String workspaceId, String instanceId, boolean preview) {
        JobConfig job = JobConfigValidator.validateConfig(parameters, factories);
        boolean prepareSchema = job.steps().contains(Step.SCHEMA);
        List<TablePlan> tables = new ArrayList<>();
        List<ExecutionResource> claims = new ArrayList<>();
        java.util.Set<TableId> sources = new java.util.HashSet<>();
        java.util.Set<TableId> targets = new java.util.HashSet<>();
        StringBuilder bindings = new StringBuilder();
        for (var mapping : job.tables()) {
            ReadonlyConfig source = mapping.source();
            ReadonlyConfig sink = mapping.sink();
            String sourceId = source.get(JobOptions.DATASOURCE_ID);
            String sinkId = sink.get(JobOptions.DATASOURCE_ID);
            var sourceFactory = factories.source(source.get(JobOptions.TYPE));
            var sinkFactory = factories.sink(sink.get(JobOptions.TYPE));
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
            SolutionCatalog.validate(job.solutionType(), from.datasourceType(), targetInfo.type());
            SolutionCatalog.validateSchemaChanges(
                    job.solutionType(), from.datasourceType(), targetInfo.type(), job.schemaChangesEnabled());
            SolutionCatalog.validateSteps(from.datasourceType(), targetInfo.type(), job.steps());
            if (job.solutionType().supportsScheduling()
                    && job.steps().contains(Step.INCREMENTAL)
                    && (!java.util.Set.of("MYSQL", "POSTGRE_SQL").contains(from.datasourceType())
                            || !java.util.Set.of("MYSQL", "POSTGRE_SQL").contains(targetInfo.type()))) {
                throw new IllegalArgumentException("Offline incremental is limited to MySQL and PostgreSQL");
            }
            if (!sources.add(from.table()) || !targets.add(targetTable)) {
                throw new IllegalArgumentException("Duplicate resolved source or destination table");
            }
            if (!tables.isEmpty()
                    && (from.revision() != tables.getFirst().source().revision()
                            || targetInfo.revision()
                                    != tables.getFirst().targetDatasource().revision())) {
                throw new IllegalStateException("Datasource revision changed while planning the table collection");
            }
            CreateTableEvent event = prepareSchema ? SchemaValidator.createEvent(from, targetInfo, targetTable) : null;
            SchemaChangePlan schema = event == null ? null : SchemaValidator.plan(event, targetInfo.type(), actual);
            if (schema != null && !preview) SchemaValidator.requireCompatible(schema);
            TableSchema expected = event == null ? actual.schema() : event.getSchema();
            if (schema == null || !schema.blocked()) {
                sinkFactory.validateSchema(sink, from.schema(), expected, sourceFactory.producesChangelog());
            }
            if (actual != null && (schema == null || (!schema.blocked() && !schema.requiresDdl()))) {
                sinkFactory.validateSchema(sink, from.schema(), actual.schema(), sourceFactory.producesChangelog());
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
            if ("MYSQL_CDC".equals(sourceFactory.factoryIdentifier())) {
                String chunkKey = source.get(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN);
                if (chunkKey != null) binding += encodePart("chunkKey") + encodePart(chunkKey);
                if (source.get(MySqlCdcSourceOptions.KEYLESS_ENABLED)) {
                    if (!"MYSQL".equals(from.datasourceType()) || !"MYSQL".equals(targetInfo.type())) {
                        throw new IllegalArgumentException("Keyless CDC product acceptance is MySQL to MySQL only");
                    }
                    binding += encodePart("keyless");
                }
            }
            if (sink.get(JdbcSinkOptions.XA_ENABLED)) {
                if (!"MYSQL".equals(targetInfo.type()) || !source.get(MySqlCdcSourceOptions.KEYLESS_ENABLED)) {
                    throw new IllegalArgumentException("MySQL XA requires a keyless MySQL CDC Source");
                }
                binding += encodePart("XA");
            }
            if (event != null) {
                binding += encodePart("SCHEMA") + encodePart(event.getTableComment());
                binding += encodeProperties(event.getColumnProperties());
                for (var comment : new java.util.TreeMap<>(event.getColumnComments()).entrySet()) {
                    binding += encodePart(comment.getKey()) + encodePart(comment.getValue());
                }
            }
            claims.add(new ExecutionResource("DATA_SYNC_TARGET", target));
            Map<String, Object> prepared =
                    new LinkedHashMap<>(JobOptions.connectorOptions(source).getSourceMap());
            if (instanceId != null && "MYSQL_CDC".equals(sourceFactory.factoryIdentifier())) {
                prepared.put(MySqlCdcSourceOptions.SERVER_ID.key(), MySqlCdcReplicationIdentity.serverId(instanceId));
                prepared.put(
                        MySqlCdcSourceOptions.TOPIC_PREFIX.key(), MySqlCdcReplicationIdentity.topicPrefix(instanceId));
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
                    actual));
        }
        if (job.sourceDataSourceId().equals(job.targetDataSourceId())
                && sources.stream().anyMatch(targets::contains)) {
            throw new IllegalArgumentException("Destinations overlap their source tables");
        }
        return new JobPlan(tables, new TaskAdmissionPlan(claims, sha256(bindings.toString())));
    }

    private static String encodeTable(TableId table) {
        return encodePart(table.catalog()) + encodePart(table.schema()) + encodePart(table.table());
    }

    /** Physical metadata changes invalidate previously admitted schema plans. */
    private static String encodeProperties(java.util.Map<String, io.yak.ops.core.event.ColumnProperties> properties) {
        StringBuilder value = new StringBuilder();
        for (var entry : new java.util.TreeMap<>(properties).entrySet()) {
            var attribute = entry.getValue();
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
