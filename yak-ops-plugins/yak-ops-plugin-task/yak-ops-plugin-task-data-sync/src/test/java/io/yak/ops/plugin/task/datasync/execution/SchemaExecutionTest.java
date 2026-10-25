package io.yak.ops.plugin.task.datasync.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSinkFactory;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceFactory;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSinkFactory;
import io.yak.ops.connector.jdbc.source.JdbcSourceFactory;
import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.datasync.pipeline.PipelineBuilder;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Exercises the real Planner, deferred Task owner and report projection with controlled external operations. */
@Timeout(20)
class SchemaExecutionTest {
    private static final TaskExecutionIdentity ID = new TaskExecutionIdentity("ws", "definition", 3, "instance-1643");
    private static final TableSchema SHAPE = new TableSchema(List.of(
            new Column("id", LogicalTypes.BIGINT.copy(false)),
            new Column("name", LogicalTypes.varchar(32))), List.of("id"));

    @Test
    void advertisesOnlyVerifiedSchemaEndpointPairsWithoutChangingDataOnlySolutions() {
        Fixture fixture = new Fixture();
        var catalog = new io.yak.ops.plugin.task.datasync.solution.SolutionCatalog(fixture.factories);
        for (String source : List.of("MYSQL", "POSTGRE_SQL", "ORACLE")) {
            for (String target : List.of("MYSQL", "POSTGRE_SQL", "ORACLE")) {
                boolean supported = !source.equals("ORACLE") && !target.equals("ORACLE");
                var choices = catalog.list(source, target);
                assertFalse(choices.isEmpty());
                for (var choice : choices) {
                    assertEquals(supported, choice.steps().contains(io.yak.ops.plugin.task.datasync.solution.Step.SCHEMA));
                    assertFalse(choice.defaultSteps().contains(io.yak.ops.plugin.task.datasync.solution.Step.SCHEMA));
                }
                if (!supported) assertThrows(IllegalArgumentException.class,
                        () -> io.yak.ops.plugin.task.datasync.solution.SolutionCatalog.validateSteps(source, target,
                                List.of(io.yak.ops.plugin.task.datasync.solution.Step.SCHEMA,
                                        io.yak.ops.plugin.task.datasync.solution.Step.FULL)));
            }
        }
    }

    @Test
    void missingTargetsPlanWithoutConnectionsAndCreationLeavesAdmissionStable() {
        Fixture f = new Fixture();
        var options = options(2, false);
        var missing = f.planner.plan(options, ID);
        assertEquals(2, missing.admissionPlan().exclusiveResources().size());
        assertNull(missing.tables().getFirst().targetSchema());
        assertEquals(0, f.connectionLookups.get());
        var preview = f.planner.preview(options, "ws");
        assertEquals("CREATE", preview.getFirst().action());
        assertTrue(preview.getFirst().statements().getFirst().contains("CREATE TABLE"));
        assertEquals(0, f.applied.get());
        missing.tables().forEach(table -> f.create(table.schemaChange()));
        var existing = f.planner.plan(options, ID);
        assertEquals(missing.admissionPlan(), existing.admissionPlan());
        assertNotNull(existing.tables().getFirst().targetSchema());
        assertEquals("VERIFY", f.planner.preview(options, "ws").getFirst().action());
        f.revision++;
        assertNotEquals(missing.admissionPlan(), f.planner.plan(options, ID).admissionPlan());
    }

    @Test
    void changedSourceMetadataOrMissingSchemaStepCannotReusePreparationAdmission() {
        Fixture f = new Fixture();
        var admitted = f.planner.plan(options(1, false), ID).admissionPlan();
        f.sourceComment = "changed comment";
        assertNotEquals(admitted, f.planner.plan(options(1, false), ID).admissionPlan());
        var dataOnly = ConfigParser.parseJson(json(1, false).replace("\"CREATE_OR_ALTER\"", "\"VERIFY\""));
        assertThrows(IllegalArgumentException.class, () -> f.planner.plan(dataOnly, ID));
        f.lookupFailure = new IllegalStateException("permission denied", new SQLException("metadata rejected", "42501"));
        assertSame(f.lookupFailure, assertThrows(IllegalStateException.class, () -> f.planner.plan(options(1, false), ID)));
        assertEquals(0, f.applied.get());
    }

    @Test
    void rejectsUnsupportedSourceFeaturesAndIncompleteExistingTargetsBeforeDdl() {
        Fixture f = new Fixture();
        for (String feature : List.of("DEFAULT", "IDENTITY", "GENERATED", "UNKNOWN_GENERATION", "NON_TABLE")) {
            f.sourceFeatures = Set.of(feature);
            assertThrows(IllegalArgumentException.class, () -> f.planner.plan(options(1, false), ID));
        }
        f.sourceFeatures = Set.of();
        TableId target = new TableId("ods", "public", "copy_0");
        f.targets.put(target, new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id")));
        assertThrows(IllegalArgumentException.class, () -> f.planner.plan(options(1, false), ID));
        assertEquals(0, f.applied.get());
        assertEquals(0, f.submitted.get());
    }

    @Test
    void dataOnlyExecutionSkipsSchemaPreparationAndSubmitsRows() throws Exception {
        Fixture f = new Fixture();
        f.targets.put(new TableId("ods", "public", "copy_0"), SHAPE);
        Map<String, Object> values = new java.util.LinkedHashMap<>(options(1, false).getSourceMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> previousSink = (Map<String, Object>) values.get("sink");
        Map<String, Object> sink = new java.util.LinkedHashMap<>(previousSink);
        sink.put("schemaPolicy", Map.of(
                "initial", "VERIFY",
                "evolution", Map.of("enabled", false, "allowDropColumns", false)));
        values.put("sink", sink);
        ReadonlyConfig parameters = ReadonlyConfig.fromMap(values);
        var admission = f.planner.admissionPlan(parameters, ID);
        TaskHandle handle = new DataSyncTaskExecutor(f.planner, f.builder, f.engine)
                .start(parameters, admission, ID, false)
                .toCompletableFuture()
                .join();
        handle.start();
        assertEquals(TaskOutcome.FINISHED, handle.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(0, f.applied.get());
        assertEquals(0, f.closed.get());
        assertEquals(1, f.submitted.get());
        assertEquals(1, JSONUtils.readTree(handle.executionReport().orElseThrow()).path("version").asInt());
    }

    @Test
    void deferredHandleDoesNothingUntilRegisteredAndHonorsCancellationBeforeActivation() throws Exception {
        Fixture f = new Fixture();
        TaskHandle handle = f.handle(1, false);
        assertFalse(handle.completion().toCompletableFuture().isDone());
        assertTrue(handle.runtimeReference().isEmpty());
        assertEquals(0, f.applied.get());
        assertEquals(0, f.connectionLookups.get());
        handle.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
        handle.start();
        assertEquals(TaskOutcome.CANCELED, handle.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(0, f.applied.get());
        assertEquals(0, f.submitted.get());
    }

    @Test
    void allTargetsVerifyBeforeOneJobStartsAndLateJobIdentityIsPreserved() throws Exception {
        Fixture f = new Fixture();
        f.engine = (graph, config) -> {
            assertEquals(2, f.applied.get());
            assertEquals(2, f.closed.get());
            assertEquals(2, f.targets.size());
            f.submitted.incrementAndGet();
            return CompletableFuture.completedFuture(new Client(JobStatus.FINISHED));
        };
        TaskHandle handle = f.handle(2, false);
        handle.start();
        assertEquals(TaskOutcome.FINISHED, handle.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(1, f.submitted.get());
        assertEquals("YAKFLOW", handle.runtimeReferenceReady().toCompletableFuture().get().orElseThrow().engine());
        JsonNode report = JSONUtils.readTree(handle.executionReport().orElseThrow());
        assertEquals(3, report.path("version").asInt());
        assertEquals(2, report.path("stages").get(0).path("completedTables").asInt());
        assertEquals(100, report.path("stages").get(0).path("progressPercent").asInt());
        for (JsonNode table : report.path("tables")) {
            assertEquals("PREPARED", table.path("stages").get(0).path("action").asText());
            assertEquals("FINISHED", table.path("stages").get(1).path("status").asText());
        }
        assertFalse(report.toString().contains("confidential-comment"));
        assertFalse(report.toString().contains("password-value"));
        assertFalse(report.toString().contains("CREATE TABLE"));
        TaskHandle again = f.handle(2, false);
        again.start();
        assertEquals(TaskOutcome.FINISHED, again.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals("VERIFIED", JSONUtils.readTree(again.executionReport().orElseThrow())
                .path("tables").get(0).path("stages").get(0).path("action").asText());
    }

    @Test
    void partialDdlFailureKeepsVerifiedTableAndNeverSubmitsRows() throws Exception {
        Fixture f = new Fixture();
        f.application = event -> {
            if (event.tableId().table().equals("copy_1")) {
                throw new SchemaEvolveException(event, "DDL rejected", new SQLException("denied", "42501"));
            }
            f.create(event);
        };
        TaskHandle handle = f.handle(3, false);
        handle.start();
        assertEquals(TaskOutcome.FAILED, handle.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(2, f.applied.get());
        assertEquals(2, f.closed.get());
        assertEquals(1, f.targets.size());
        assertEquals(0, f.submitted.get());
        JsonNode report = JSONUtils.readTree(handle.executionReport().orElseThrow());
        assertEquals("FINISHED", report.path("tables").get(0).path("stages").get(0).path("status").asText());
        assertEquals("FAILED", report.path("tables").get(1).path("stages").get(0).path("status").asText());
        assertEquals("CANCELED", report.path("tables").get(2).path("stages").get(0).path("status").asText());
        assertEquals(1, report.path("stages").get(0).path("completedTables").asInt());
        assertEquals(33, report.path("stages").get(0).path("progressPercent").asInt());
        assertTrue(report.path("stages").get(1).path("readRows").isNull());
        assertTrue(handle.runtimeReferenceReady().toCompletableFuture().get().isEmpty());
    }

    @Test
    void cancellationDoesNotCompleteOrCloseUntilTheActiveOperationReturns() throws Exception {
        Fixture f = new Fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        f.application = event -> {
            entered.countDown();
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("DDL release not received");
                        break;
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
                f.create(event);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        };
        TaskHandle handle = f.handle(2, false);
        handle.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            handle.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, f.canceled.get());
            assertFalse(handle.completion().toCompletableFuture().isDone());
            assertEquals(0, f.closed.get());
            assertEquals(0, f.submitted.get());
        } finally {
            release.countDown();
        }
        assertEquals(TaskOutcome.CANCELED, handle.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(1, f.closed.get());
        assertEquals(1, f.applied.get());
        assertEquals(0, f.submitted.get());
    }

    @Test
    void cancellationDuringSubmissionWaitsForLateClientAndPhysicalOutcome() throws Exception {
        Fixture f = new Fixture();
        CountDownLatch submitted = new CountDownLatch(1);
        CompletableFuture<JobClient> delayed = new CompletableFuture<>();
        f.engine = (graph, config) -> {
            f.submitted.incrementAndGet();
            submitted.countDown();
            return delayed;
        };
        TaskHandle handle = f.handle(1, true);
        handle.start();
        assertTrue(submitted.await(5, TimeUnit.SECONDS));
        handle.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertFalse(handle.completion().toCompletableFuture().isDone());
        Client client = new Client(JobStatus.RUNNING);
        delayed.complete(client);
        assertTrue(client.cancelRequested.await(5, TimeUnit.SECONDS));
        assertFalse(handle.completion().toCompletableFuture().isDone());
        assertEquals(client.id.toString(), handle.runtimeReferenceReady().toCompletableFuture().get().orElseThrow().id());
        client.finish(JobStatus.CANCELED);
        assertEquals(TaskOutcome.CANCELED, handle.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        JsonNode report = JSONUtils.readTree(handle.executionReport().orElseThrow());
        assertEquals("FINISHED", report.path("stages").get(0).path("status").asText());
        assertTrue(report.path("stages").get(2).path("progressPercent").isNull());
    }

    @Test
    void uncertainEngineSubmissionRemainsExceptionalRatherThanReleasingOwnership() throws Exception {
        Fixture f = new Fixture();
        f.engine = (graph, config) -> CompletableFuture.failedFuture(new IllegalStateException("unknown engine result"));
        TaskHandle handle = f.handle(1, false);
        handle.start();
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> handle.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals("FINISHED", JSONUtils.readTree(handle.executionReport().orElseThrow())
                .path("stages").get(0).path("status").asText());
    }

    @Test
    void unconfirmedDdlOutcomeKeepsPartialReportAndCannotBecomeAnAuthoritativeFailure() throws Exception {
        Fixture f = new Fixture();
        f.application = event -> {
            if (event.tableId().table().equals("copy_1")) {
                throw new SchemaEvolveException(event, "DDL response lost", new SQLException("connection lost", "08006"), true);
            }
            f.create(event);
        };
        TaskHandle handle = f.handle(3, false);
        handle.start();
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> handle.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(0, f.submitted.get());
        JsonNode report = JSONUtils.readTree(handle.executionReport().orElseThrow());
        assertEquals("UNKNOWN", report.path("stages").get(0).path("status").asText());
        assertEquals(1, report.path("stages").get(0).path("completedTables").asInt());
        assertEquals("FINISHED", report.path("tables").get(0).path("stages").get(0).path("status").asText());
        assertEquals("UNKNOWN", report.path("tables").get(1).path("stages").get(0).path("status").asText());
        assertTrue(handle.runtimeReference().isEmpty());
    }

    private static ReadonlyConfig options(int count, boolean realtime) {
        return ConfigParser.parseJson(json(count, realtime));
    }

    private static String json(int count, boolean realtime) {
        List<Map<String, Object>> sources = new java.util.ArrayList<>();
        List<Map<String, Object>> sinks = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            String id = "route" + index;
            sources.add(Map.of("id", id, "database", "app", "table", "orders_" + index));
            sinks.add(Map.of(
                    "input", id, "database", "ods", "schema", "public", "table", "copy_" + index,
                    "write", Map.of("mode", "UPSERT")));
        }
        return JSONUtils.toJson(Map.of(
                "solutionType", "DATABASE_" + (realtime ? "REALTIME" : "OFFLINE"),
                "steps", realtime ? List.of("FULL", "INCREMENTAL") : List.of("FULL"),
                "source", Map.of("datasourceId", "source", "tables", sources),
                "transform", List.of(),
                "sink", Map.of(
                        "datasourceId", "target",
                        "schemaPolicy", Map.of("initial", "CREATE_OR_ALTER",
                                "evolution", Map.of("enabled", false, "allowDropColumns", false)),
                        "tables", sinks),
                "env", Map.of()));
    }

    private static final class Fixture implements DataSourceLookupService {
        final Map<TableId, TableSchema> targets = new ConcurrentHashMap<>();
        final AtomicInteger applied = new AtomicInteger();
        final AtomicInteger closed = new AtomicInteger();
        final AtomicInteger canceled = new AtomicInteger();
        final AtomicInteger submitted = new AtomicInteger();
        final AtomicInteger connectionLookups = new AtomicInteger();
        long revision = 1;
        String sourceComment = "confidential-comment";
        Set<String> sourceFeatures = Set.of();
        RuntimeException lookupFailure;
        Consumer<CreateTableEvent> application = this::create;
        PipelineExecutor engine = (graph, config) -> {
            submitted.incrementAndGet();
            return CompletableFuture.completedFuture(new Client(JobStatus.FINISHED));
        };
        final FactoryUtil factories = new FactoryUtil(
                List.of(new JdbcSourceFactory(), new MySqlCdcSourceFactory()), List.of(new TestSink()));
        final JobPlanner planner = new JobPlanner(this, factories);
        final PipelineBuilder builder = new PipelineBuilder(this, factories, Path.of("target/schema-execution"));

        TaskHandle handle(int count, boolean realtime) {
            var parameters = options(count, realtime);
            var admission = planner.admissionPlan(parameters, ID);
            return new DataSyncTaskExecutor(planner, builder, engine)
                    .start(parameters, admission, ID, false).toCompletableFuture().join();
        }

        void create(CreateTableEvent event) {
            targets.put(event.tableId(), event.getSchema());
        }

        @Override
        public DataSourceInfo dataSource(String workspace, String datasource) {
            assertEquals("ws", workspace);
            return "source".equals(datasource) ? new DataSourceInfo("MYSQL", "app", null, 1)
                    : new DataSourceInfo("POSTGRE_SQL", "ods", "public", revision);
        }

        @Override
        public DataSourceTable table(String workspace, String datasource, String database, String schema, String table) {
            return findTable(workspace, datasource, database, schema, table)
                    .orElseThrow(() -> new IllegalArgumentException("Table absent"));
        }

        @Override
        public Optional<DataSourceTable> findTable(String workspace, String datasource, String database, String schema, String table) {
            assertEquals("ws", workspace);
            if ("source".equals(datasource)) {
                Map<String, io.yak.ops.core.event.ColumnProperties> properties = plainProperties();
                if (sourceFeatures.contains("DEFAULT")) {
                    properties = Map.of(
                            "id", new io.yak.ops.core.event.ColumnProperties(null, false, false),
                            "name", new io.yak.ops.core.event.ColumnProperties("source-default", false, false, null, false));
                } else if (sourceFeatures.contains("IDENTITY")) {
                    properties = Map.of(
                            "id", new io.yak.ops.core.event.ColumnProperties(null, true, false),
                            "name", new io.yak.ops.core.event.ColumnProperties(null, false, false));
                } else if (sourceFeatures.contains("GENERATED")) {
                    properties = Map.of(
                            "id", new io.yak.ops.core.event.ColumnProperties(null, false, false),
                            "name", new io.yak.ops.core.event.ColumnProperties(null, false, true));
                }
                return Optional.of(new DataSourceTable("MYSQL", new TableId("app", null, table),
                        SHAPE, 1, sourceComment, Map.of(), sourceFeatures, properties));
            }
            if (lookupFailure != null) throw lookupFailure;
            TableId id = new TableId("ods", "public", table);
            TableSchema shape = targets.get(id);
            return shape == null ? Optional.empty() : Optional.of(new DataSourceTable("POSTGRE_SQL",
                    id, shape, revision, sourceComment, Map.of(), Set.of(), plainProperties()));
        }

        private Map<String, io.yak.ops.core.event.ColumnProperties> plainProperties() {
            return Map.of(
                    "id", new io.yak.ops.core.event.ColumnProperties(null, false, false),
                    "name", new io.yak.ops.core.event.ColumnProperties(null, false, false));
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String datasource) {
            return connectionOptions(workspace, datasource, "source".equals(datasource) ? 1 : revision);
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String datasource, long expectedRevision) {
            assertEquals("ws", workspace);
            assertEquals("source".equals(datasource) ? 1 : revision, expectedRevision);
            connectionLookups.incrementAndGet();
            boolean source = "source".equals(datasource);
            return new Configuration().set(JdbcConnectionOptions.URL, source ? "jdbc:mysql://localhost/app" : "jdbc:postgresql://localhost/ods")
                    .set(JdbcConnectionOptions.DATABASE_TYPE, source ? "MYSQL" : "POSTGRE_SQL")
                    .set(JdbcConnectionOptions.USERNAME, "test-user")
                    .set(JdbcConnectionOptions.PASSWORD, "password-value")
                    .set(JdbcConnectionOptions.HOST, "localhost")
                    .set(JdbcConnectionOptions.PORT, source ? 3306 : 5432).toReadonlyConfig();
        }

        final class TestSink implements TableSinkFactory {
            final JdbcSinkFactory delegate = new JdbcSinkFactory();
            @Override public String factoryIdentifier() { return delegate.factoryIdentifier(); }
            @Override public OptionRule optionRule() { return delegate.optionRule(); }
            @Override public OptionRule creationOptionRule() { return delegate.creationOptionRule(); }
            @Override public void validate(ReadonlyConfig options, boolean changelog) { delegate.validate(options, changelog); }
            @Override public void validateSchema(ReadonlyConfig options, TableSchema source, TableSchema target, boolean changelog) {
                delegate.validateSchema(options, source, target, changelog);
            }
            @Override public Sink<TableRecord> createSink(TableSinkFactoryContext context) { return delegate.createSink(context); }
            @Override public Sink<TableRecord> createSink(List<TableSinkFactoryContext> contexts) { return delegate.createSink(contexts); }
            @Override public MetadataApplier createMetadataApplier(ReadonlyConfig options) {
                return new MetadataApplier() {
                    @Override public void applySchemaChange(SchemaChangeEvent event) {
                        applied.incrementAndGet();
                        application.accept((CreateTableEvent) event);
                    }
                    @Override public void cancel() { canceled.incrementAndGet(); }
                    @Override public void close() { closed.incrementAndGet(); }
                };
            }
        }
    }

    private static final class Client implements JobClient {
        final JobID id = JobID.generate();
        volatile JobStatus status;
        final CompletableFuture<JobExecutionResult> result = new CompletableFuture<>();
        final CountDownLatch cancelRequested = new CountDownLatch(1);
        Client(JobStatus state) {
            status = state;
            if (state.isTerminalState()) finish(state);
        }
        void finish(JobStatus state) { status = state; result.complete(new JobExecutionResult(id, 1)); }
        @Override public JobID getJobID() { return id; }
        @Override public CompletableFuture<JobStatus> getJobStatus() { return CompletableFuture.completedFuture(status); }
        @Override public CompletableFuture<JobExecutionResult> getJobExecutionResult() { return result; }
        @Override public CompletableFuture<Void> cancel() { cancelRequested.countDown(); return CompletableFuture.completedFuture(null); }
    }
}
