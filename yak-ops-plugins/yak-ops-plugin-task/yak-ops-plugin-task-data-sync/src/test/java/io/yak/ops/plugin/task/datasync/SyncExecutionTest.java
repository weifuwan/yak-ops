package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.datasync.execution.SyncExecutionPlanner;
import io.yak.ops.plugin.task.datasync.execution.SyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies product DATA_SYNC planning and execution without opening physical JDBC connections. */
class SyncExecutionTest {
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(32))),
            List.of("id"));
    private static final TaskExecutionContext CONTEXT =
            new TaskExecutionContext("ws", "definition", 1, "instance-123", "attempt-123");

    @Test
    void boundedJdbcRunsThroughExistingPipelineExecutor() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(false);
        TaskExecutionPlan plan = plugin.plan(parameters, CONTEXT);
        assertEquals(1, plan.exclusiveResources().size());
        assertEquals("DATA_SYNC_TARGET", plan.exclusiveResources().getFirst().type());
        assertEquals(0, resources.connections.get());

        var handle = plugin.start(parameters, plan, CONTEXT).toCompletableFuture().join();
        assertEquals(1, submissions.get());
        assertEquals("YAKFLOW", handle.runtimeReference().orElseThrow().engine());
        assertEquals(TaskOutcome.FINISHED, handle.completion().toCompletableFuture().join());
        assertEquals(0, resources.openedConnections.get());
        assertEquals(4, resources.tables.get());
        assertEquals(2, resources.connections.get());
    }

    @Test
    void refusesChangedDatasourceRevisionBeforeLaunching() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(false);
        TaskExecutionPlan admitted = plugin.plan(parameters, CONTEXT);
        resources.targetRevision++;
        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> plugin.start(parameters, admitted, CONTEXT).toCompletableFuture().join());
        assertInstanceOf(TaskStartRejectedException.class, failure.getCause());
        assertEquals(0, submissions.get());
        assertEquals(0, resources.connections.get());
        assertEquals(4, resources.tables.get());
    }

    @Test
    void mySqlCdcBuildsHybridSnapshotPipeline() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(true);
        TaskExecutionPlan plan = plugin.plan(parameters, CONTEXT);
        assertTrue(plan.resourceRevision().length() <= 128);
        var handle = plugin.start(parameters, plan, CONTEXT).toCompletableFuture().join();
        assertEquals("YAKFLOW", handle.runtimeReference().orElseThrow().engine());
        assertEquals(1, submissions.get());
        assertEquals(0, resources.openedConnections.get());
    }

    private static SyncTaskPlugin plugin(SyncRuntimeResources resources, PipelineExecutor executor) {
        SyncExecutionPlanner planner = new SyncExecutionPlanner(resources);
        return new SyncTaskPlugin(
                planner, new SyncTaskExecutor(resources, planner, executor, Path.of("target/data-sync")));
    }

    private static SyncParameters parameters(boolean cdc) {
        var source = cdc
                ? new MySqlCdcSourceParameters("source", null, null, "orders")
                : new JdbcSourceParameters("source", null, null, "orders");
        return new SyncParameters(
                source,
                List.of(),
                new JdbcSinkParameters(
                        "target", null, null, "ods_orders", cdc ? DataSyncWriteMode.UPSERT : DataSyncWriteMode.APPEND));
    }

    private static PipelineExecutor finishedExecutor(AtomicInteger submissions) {
        return (Pipeline graph, Configuration config) -> {
            submissions.incrementAndGet();
            JobID id = JobID.generate();
            JobClient client = new JobClient() {
                @Override
                public JobID getJobID() {
                    return id;
                }

                @Override
                public CompletableFuture<JobStatus> getJobStatus() {
                    return CompletableFuture.completedFuture(JobStatus.FINISHED);
                }

                @Override
                public CompletableFuture<Void> cancel() {
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletableFuture<JobExecutionResult> getJobExecutionResult() {
                    return CompletableFuture.completedFuture(new JobExecutionResult(id, 1));
                }
            };
            return CompletableFuture.completedFuture(client);
        };
    }

    private static final class Resources implements SyncRuntimeResources {
        private final AtomicInteger connections = new AtomicInteger();
        private final AtomicInteger tables = new AtomicInteger();
        private final AtomicInteger openedConnections = new AtomicInteger();
        private long targetRevision = 1;

        @Override
        public SyncRuntimeTable table(
                String workspace, String datasource, String database, String schema, String table) {
            assertEquals("ws", workspace);
            tables.incrementAndGet();
            if ("source".equals(datasource)) {
                return new SyncRuntimeTable("MYSQL", new TableId("app", null, "orders"), SCHEMA, 1);
            }
            return new SyncRuntimeTable(
                    "POSTGRE_SQL", new TableId(null, "public", "ods_orders"), SCHEMA, targetRevision);
        }

        @Override
        public SyncRuntimeConnection connection(String workspace, String datasource) {
            connections.incrementAndGet();
            JdbcConnectionProvider provider = () -> {
                openedConnections.incrementAndGet();
                throw new SQLException("Physical JDBC connections are not opened during graph planning");
            };
            if ("source".equals(datasource)) {
                return new SyncRuntimeConnection(
                        provider, "jdbc:mysql://localhost:3306/app", "MYSQL",
                        "user", "secret", "localhost", 3306, 1, false);
            }
            return new SyncRuntimeConnection(
                    provider, "jdbc:postgresql://localhost:5432/app", "POSTGRE_SQL",
                    "user", "secret", "localhost", 5432, targetRevision, false);
        }
    }
}
