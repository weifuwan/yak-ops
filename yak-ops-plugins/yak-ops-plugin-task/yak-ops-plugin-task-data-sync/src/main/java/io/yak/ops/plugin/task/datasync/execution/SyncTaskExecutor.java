package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Verifies admitted table bindings and submits one DATA_SYNC Pipeline to YakFlow. */
public final class SyncTaskExecutor {

    private final SyncRuntimeResources resources;
    private final SyncExecutionPlanner planner;
    private final PipelineExecutor executor;
    private final Path stateRoot;

    public SyncTaskExecutor(
            SyncRuntimeResources resources, SyncExecutionPlanner planner, PipelineExecutor executor, Path stateRoot) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.stateRoot =
                Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
    }

    /**
     * Starts one physical Job after rechecking the durable resource binding.
     *
     * @param parameters pinned Task parameters
     * @param plan admitted canonical resource claim
     * @param context trusted Task execution identity
     * @return asynchronous execution handle or pre-submission rejection
     */
    public CompletionStage<TaskHandle> start(
            TaskParameters parameters, TaskExecutionPlan plan, TaskExecutionContext context) {
        boolean runtimeSubmissionInvoked = false;
        try {
            if (!(parameters instanceof SyncParameters pipeline)) {
                throw new IllegalArgumentException("Invalid DATA_SYNC parameter type");
            }
            SyncPipelinePlan prepared = planner.prepare(pipeline, context);
            if (!plan.equals(prepared.resourcePlan())) {
                throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
            }
            SourceParameters source = pipeline.source();
            JdbcSinkParameters sink = (JdbcSinkParameters) pipeline.sink();
            SyncRuntimeTable from = prepared.source();
            SyncRuntimeTable to = prepared.target();
            SyncRuntimeConnection sourceConnection = resources.connection(context.workspaceId(), source.datasourceId());
            SyncRuntimeConnection targetConnection = resources.connection(context.workspaceId(), sink.datasourceId());
            if (sourceConnection.revision() != from.revision() || targetConnection.revision() != to.revision()) {
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
}
