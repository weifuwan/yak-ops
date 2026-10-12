package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * DATA_SYNC definition and optional execution capability backed by YakFlow.
 *
 * <p>Instances and durable dispatch ownership remain Task platform responsibilities.
 */
public final class SyncPlugin implements ExecutableTaskPlugin {

    public static final String TYPE = "DATA_SYNC";

    private final SyncRuntimeResources resources;
    private final PipelineExecutor executor;
    private final Path stateRoot;

    public SyncPlugin(SyncRuntimeResources resources, PipelineExecutor executor, Path stateRoot) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.stateRoot =
                Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int parameterSchemaVersion() {
        return 2;
    }

    @Override
    public TaskParameters parse(String content) {
        SyncParameters parameters;
        try {
            parameters = JSONUtils.parseObject(content, SyncParameters.class);
        } catch (IllegalArgumentException exception) {
            throw new TaskPluginException("Invalid DATA_SYNC pipeline JSON");
        }
        SyncValidator.checkParameters(parameters);
        return parameters;
    }

    @Override
    public void validate(TaskParameters parameters, TaskValidationContext context) {
        if (!(parameters instanceof SyncParameters pipeline)) {
            throw new TaskPluginException("Invalid DATA_SYNC parameter type");
        }
        SyncValidator.checkResources(pipeline, context);
    }

    @Override
    public TaskExecutionPlan plan(TaskParameters parameters, TaskExecutionContext context) {
        if (!(parameters instanceof SyncParameters pipeline)) {
            throw new TaskPluginException("Invalid DATA_SYNC parameter type");
        }
        SyncValidator.checkParameters(pipeline);
        SourceParameters source = pipeline.source();
        JdbcSinkParameters sink = (JdbcSinkParameters) pipeline.sink();
        SyncRuntimeTable from = resources.table(
                context.workspaceId(), source.datasourceId(), source.database(), source.schema(), source.table());
        SyncRuntimeTable to = resources.table(
                context.workspaceId(), sink.datasourceId(), sink.database(), sink.schema(), sink.table());
        if (source instanceof MySqlCdcSourceParameters && !"MYSQL".equals(from.datasourceType())) {
            throw new TaskPluginException("MySQL CDC requires a MySQL datasource");
        }
        SyncValidator.checkSchemaCompatibility(
                from.schema(),
                to.schema(),
                source instanceof MySqlCdcSourceParameters
                        || sink.writeMode() == io.yak.ops.common.enums.datasync.DataSyncWriteMode.UPSERT);
        String target = encodePart(sink.datasourceId())
                + encodePart(to.table().catalog())
                + encodePart(to.table().schema())
                + encodePart(to.table().table());
        String binding = context.workspaceId()
                + "|" + source.datasourceId() + "|" + from.revision() + "|" + encodeTable(from.table())
                + "|" + sink.datasourceId() + "|" + to.revision() + "|" + encodeTable(to.table());
        return new TaskExecutionPlan(List.of(new ExecutionResource("DATA_SYNC_TARGET", target)), sha256(binding));
    }

    @Override
    public CompletionStage<TaskHandle> start(
            TaskParameters parameters, TaskExecutionPlan plan, TaskExecutionContext context) {
        boolean runtimeSubmissionInvoked = false;
        try {
            if (!(parameters instanceof SyncParameters pipeline) || !plan.equals(plan(parameters, context))) {
                throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
            }
            SourceParameters source = pipeline.source();
            JdbcSinkParameters sink = (JdbcSinkParameters) pipeline.sink();
            SyncRuntimeTable from = resources.table(
                    context.workspaceId(), source.datasourceId(), source.database(), source.schema(), source.table());
            SyncRuntimeTable to = resources.table(
                    context.workspaceId(), sink.datasourceId(), sink.database(), sink.schema(), sink.table());
            SyncRuntimeConnection sourceConnection = resources.connection(context.workspaceId(), source.datasourceId());
            SyncRuntimeConnection targetConnection = resources.connection(context.workspaceId(), sink.datasourceId());
            if (sourceConnection.revision() != from.revision()
                    || targetConnection.revision() != to.revision()
                    || !plan.equals(plan(parameters, context))) {
                throw new IllegalStateException("DATA_SYNC datasource revision changed before execution");
            }
            Configuration configuration = new Configuration()
                    .set(PipelineOptions.AUTO_GENERATE_UIDS, false)
                    .set(
                            CheckpointingOptions.STATE_DIRECTORY,
                            stateRoot.resolve(context.instanceId()).toString());
            io.yak.ops.core.api.connector.source.Source<TableRecord, ?, ?> physicalSource;
            if (source instanceof JdbcSourceParameters) {
                physicalSource = new JdbcSource(
                        sourceConnection.provider(), sourceConnection.jdbcUrl(), List.of(from.table()), configuration);
            } else if (source instanceof MySqlCdcSourceParameters) {
                if (sourceConnection.tunneled()
                        || sourceConnection.host() == null
                        || !"MYSQL".equals(sourceConnection.datasourceType())) {
                    throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
                }
                int serverId = Math.floorMod(context.instanceId().hashCode(), 2_000_000_000) + 1;
                physicalSource = MySqlCdcSource.builder()
                        .hostname(sourceConnection.host())
                        .port(sourceConnection.port())
                        .username(sourceConnection.username())
                        .password(sourceConnection.password())
                        .serverId(serverId)
                        .topicPrefix("yakflow_" + context.instanceId())
                        .table(from.table(), from.schema())
                        .buildHybrid(1024);
                configuration.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(10));
            } else {
                throw new UnsupportedOperationException("Unsupported DATA_SYNC source");
            }
            JdbcWriteMode writeMode = JdbcWriteMode.valueOf(sink.writeMode().name());
            JdbcTableWritePlan route =
                    new JdbcTableWritePlan(from.table(), to.table(), to.schema(), writeMode, from.schema(), Map.of());
            JdbcSink physicalSink = JdbcSink.builder()
                    .withConnectionProvider(targetConnection.provider())
                    .withDialect(JdbcFactoryLoader.loadDialect(targetConnection.jdbcUrl()))
                    .withTablePlans(List.of(route))
                    .build();
            SourceTransformation<TableRecord> input =
                    new SourceTransformation<>("data-sync-source", physicalSource, TableRecord.class, 1);
            SinkTransformation<TableRecord> output = new SinkTransformation<>(input, "data-sync-sink", physicalSink, 1);
            input.setUid("data-sync-source-" + context.definitionId());
            output.setUid("data-sync-sink-" + context.definitionId());
            StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
            runtimeSubmissionInvoked = true;
            return executor.execute(graph, configuration).thenApply(client -> (TaskHandle) new SyncJobHandle(client));
        } catch (RuntimeException error) {
            if (!runtimeSubmissionInvoked) {
                return CompletableFuture.failedFuture(
                        new TaskStartRejectedException("DATA_SYNC rejected before engine submission", error));
            }
            return CompletableFuture.failedFuture(error);
        }
    }

    private static String encodeTable(TableId table) {
        return encodePart(table.catalog()) + encodePart(table.schema()) + encodePart(table.table());
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
