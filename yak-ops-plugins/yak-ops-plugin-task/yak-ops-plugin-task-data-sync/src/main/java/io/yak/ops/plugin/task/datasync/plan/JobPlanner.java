package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.validation.JobConfigValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves authorized DATA_SYNC metadata and constructs the logical Job and admission plans. */
public final class JobPlanner {

    private final DataSourceLookupService resources;
    private final FactoryUtil factories;

    public JobPlanner(DataSourceLookupService resources, FactoryUtil factories) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    /**
     * Declares the exclusive destination and its non-secret binding fingerprint.
     *
     * @param parameters pinned DATA_SYNC definition
     * @param context trusted Task execution identity
     * @return admission claim validated against the effective schema
     */
    public TaskAdmissionPlan admissionPlan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
        return plan(parameters, context).admissionPlan();
    }

    /**
     * Resolves source and target metadata and validates the executable JDBC Sink projection.
     *
     * <p>Planning reads authorized metadata; it does not export connection settings or submit jobs.
     *
     * @param parameters pinned DATA_SYNC definition
     * @param context trusted Task execution identity
     * @return immutable, non-secret logical DATA_SYNC plan
     */
    public JobPlan plan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
        JobConfig job = JobConfigValidator.validateConfig(parameters, factories);
        ReadonlyConfig source = job.source();
        ReadonlyConfig sink = job.sink();
        String sourceId = source.get(JobOptions.DATASOURCE_ID);
        String sinkId = sink.get(JobOptions.DATASOURCE_ID);
        var sourceFactory = factories.source(source.get(JobOptions.TYPE));
        var sinkFactory = factories.sink(sink.get(JobOptions.TYPE));
        DataSourceTable from = resources.table(
                context.workspaceId(),
                sourceId,
                source.get(JobOptions.DATABASE),
                source.get(JobOptions.SCHEMA),
                source.get(JobOptions.TABLE));
        DataSourceTable to = resources.table(
                context.workspaceId(),
                sinkId,
                sink.get(JobOptions.DATABASE),
                sink.get(JobOptions.SCHEMA),
                sink.get(JobOptions.TABLE));

        sourceFactory.validateDatasource(from.datasourceType());
        sinkFactory.validateSchema(sink, from.schema(), to.schema(), sourceFactory.producesChangelog());

        String target = encodePart(sinkId) + encodeTable(to.table());
        String binding = encodePart(context.workspaceId())
                + encodePart(sourceId)
                + encodePart(Long.toString(from.revision()))
                + encodeTable(from.table())
                + encodeSchema(from.schema())
                + encodePart(sinkId)
                + encodePart(Long.toString(to.revision()))
                + encodeTable(to.table())
                + encodeSchema(to.schema());
        List<ExecutionResource> claims = new ArrayList<>();
        claims.add(new ExecutionResource("DATA_SYNC_TARGET", target));
        Map<String, Object> prepared =
                new LinkedHashMap<>(JobOptions.connectorOptions(source).getSourceMap());
        if ("MYSQL_CDC".equals(sourceFactory.factoryIdentifier())) {
            String instanceId = context.instanceId();
            prepared.put(MySqlCdcSourceOptions.SERVER_ID.key(), MySqlCdcReplicationIdentity.serverId(instanceId));
            prepared.put(MySqlCdcSourceOptions.TOPIC_PREFIX.key(), MySqlCdcReplicationIdentity.topicPrefix(instanceId));
            claims.add(MySqlCdcReplicationIdentity.exclusiveResource(instanceId));
        }
        TaskAdmissionPlan admission = new TaskAdmissionPlan(claims, sha256(binding));
        return new JobPlan(source, sink, from, to, admission, ReadonlyConfig.fromMap(prepared));
    }

    private static String encodeTable(TableId table) {
        return encodePart(table.catalog()) + encodePart(table.schema()) + encodePart(table.table());
    }

    private static String encodeSchema(TableSchema schema) {
        StringBuilder value = new StringBuilder().append(schema.columnCount()).append(':');
        for (Column column : schema.columns()) {
            value.append(encodePart(column.name()));
            value.append(encodePart(column.dataType().asSerializableString()));
        }
        value.append(schema.primaryKeys().size()).append(':');
        for (String key : schema.primaryKeys()) {
            value.append(encodePart(key));
        }
        return value.toString();
    }

    private static String encodePart(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }
}
