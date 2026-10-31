package io.yak.ops.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.composer.yakflow.EnvironmentOptions;
import io.yak.ops.composer.yakflow.ExecutionConfigurationFactory;
import io.yak.ops.composer.yakflow.YakFlowPipelineComposer;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.connector.jdbc.sink.MySqlDataSinkFactory;
import io.yak.ops.connector.jdbc.source.MySqlDataSourceFactory;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.flink.cdc.common.pipeline.PipelineOptions;
import org.apache.flink.cdc.common.pipeline.RuntimeExecutionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** CLI-specific parsing, credential admission, discovery and physical-lifetime contracts. */
@Timeout(20)
class CliFrontendTest {
    private static final TableId SOURCE = new TableId("shop", null, "orders");
    private static final TableId TARGET = new TableId("warehouse", null, "orders_copy");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.INTEGER.copy(false))), List.of("id"));
    private static final FactoryUtil FACTORIES = new FactoryUtil(List.of(new MySqlDataSourceFactory(),
            new io.yak.ops.connector.cdc.mysql.factory.MySqlDataSourceFactory()), List.of(new MySqlDataSinkFactory()));

    @TempDir
    Path directory;

    @Test
    void fileParsingUsesExplicitRuntimeOverrideAndRejectsAmbiguousArguments() throws Exception {
        Path file = directory.resolve("pipeline.yaml");
        Files.writeString(file, yaml().replace("execution.runtime-mode: BATCH", "execution.runtime-mode: STREAMING"));
        PipelineDef definition = CliFrontend.parseDefinition(CliFrontend.Arguments.parse(new String[] {
                "--runtime-mode", "BATCH", "--state-dir", directory.toString(), file.toString()
        }));
        assertEquals(RuntimeExecutionMode.BATCH, definition.getConfig().get(PipelineOptions.PIPELINE_EXECUTION_RUNTIME_MODE));
        assertThrows(IllegalArgumentException.class, () -> CliFrontend.Arguments.parse(new String[] {"--state-dir"}));
        assertThrows(IllegalArgumentException.class, () -> CliFrontend.Arguments.parse(new String[] {"one.yaml", "two.yaml"}));
        assertThrows(IllegalArgumentException.class, () -> CliFrontend.Arguments.parse(new String[] {
                "--runtime-mode", "BATCH", "--runtime-mode", "STREAMING", file.toString()
        }));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(0, CliFrontend.run(new String[] {"--help"}, new PrintStream(output), new PrintStream(output)));
        assertTrue(output.toString().contains("--state-dir"));
    }

    @Test
    void cliDiscoveryUsesUpstreamSelectorsAndPreservesMetadataWithoutPlatformLookup() {
        PipelineDef raw = new YamlPipelineDefinitionParser().parse(yaml());
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        CliDataSourceLookupService lookup = lookup(raw, opened, closed);
        PipelineDef admitted = lookup.resolve(raw);
        assertEquals("shop.orders", admitted.getSource().getConfig().toMap().get("tables"));
        assertEquals("DATABASE", admitted.getSource().getConfig().toMap().get("yak.namespace-kind"));
        assertFalse(admitted.getSource().getConfig().toMap().containsKey("tables.exclude"));
        assertFalse(admitted.getSource().getConfig().toMap().containsKey("password"));
        assertFalse(admitted.getSource().getConfig().toMap().containsKey("hostname"));
        ReadonlyConfig connection = lookup.connectionOptions("cli-test", CliDataSourceLookupService.SOURCE_ID, 1);
        assertEquals("jdbc:mysql://source.example:3306/", connection.get(JdbcConnectionOptions.URL));
        assertEquals("reader", connection.get(JdbcConnectionOptions.USERNAME));
        assertEquals("source-secret", connection.get(JdbcConnectionOptions.PASSWORD));
        DataSourceTable table = lookup.table("cli-test", CliDataSourceLookupService.SOURCE_ID, "shop", null, "orders");
        assertEquals("order identifier", table.columnComments().get("id"));
        assertEquals("int", table.columnProperties().get("id").nativeType());
        assertEquals(List.of(List.of("id")), table.uniqueKeys());
        assertEquals(opened.get(), closed.get(), "Every catalog must be closed after its operation");
        int accesses = opened.get();
        assertThrows(IllegalArgumentException.class, () -> lookup.dataSource("saved-workspace", CliDataSourceLookupService.SOURCE_ID));
        assertThrows(IllegalArgumentException.class, () -> lookup.connectionOptions("cli-test", "saved-datasource"));
        assertThrows(IllegalArgumentException.class, () -> lookup.table("cli-test", CliDataSourceLookupService.SOURCE_ID, "shop", null, "orders_archive"));
        assertThrows(IllegalStateException.class, () -> lookup.connectionOptions("cli-test", CliDataSourceLookupService.SOURCE_ID, 2));
        assertEquals(accesses, opened.get(), "Rejected lookups must not touch credentials or metadata");
    }

    @Test
    void resolvedCliYamlHasTheSameComposerAdmissionAsExplicitEmbeddedYaml() {
        PipelineDef raw = new YamlPipelineDefinitionParser().parse(yaml());
        CliDataSourceLookupService lookup = lookup(raw, new AtomicInteger(), new AtomicInteger());
        PipelineDef admitted = lookup.resolve(raw);
        PipelineDef explicit = new YamlPipelineDefinitionParser().parse("""
                source:
                  type: jdbc-mysql
                  datasource-id: cli-source
                  tables: shop.orders
                  yak.namespace-kind: DATABASE
                sink:
                  type: jdbc-mysql
                  datasource-id: cli-sink
                  yak.namespace-kind: DATABASE
                  write.mode: APPEND
                route:
                  - source-table: shop.orders
                    sink-table: warehouse.orders_copy
                pipeline:
                  execution.runtime-mode: BATCH
                  local-time-zone: UTC
                """);
        ReadonlyConfig environment = EnvironmentOptions.defaults();
        YakFlowPipelineComposer composer = new YakFlowPipelineComposer(lookup, FACTORIES, environment,
                new ExecutionConfigurationFactory(environment, directory), lookup.sourceConnectorOptions(),
                lookup.sinkConnectorOptions(), new EmbeddedPipelineExecutor(),
                new IncrementalBatchPreparation(lookup, (identity, proposed) -> {
                    throw new AssertionError("Full CLI batches do not enter the Task cursor path");
                }));
        PipelineContext context = new PipelineContext("cli-test", "cli-pipeline", 1, "instance", false);
        PipelinePlan cli = composer.plan(admitted, context);
        PipelinePlan embedded = composer.plan(explicit, context);
        assertTrue(cli.sameAdmission(embedded));
        assertEquals(SOURCE, cli.tables().getFirst().source().table());
        assertEquals(TARGET, cli.tables().getFirst().targetTable());
        assertEquals(SCHEMA, cli.tables().getFirst().writeSourceSchema());
    }

    @Test
    void conflictingConnectionsSavedReferencesAndSelfWritesFailBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> lookup(new YamlPipelineDefinitionParser().parse(
                yaml().replace("hostname: source.example", "hostname: source.example\n  connection.url: jdbc:mysql://other/")),
                new AtomicInteger(), new AtomicInteger()));
        assertThrows(IllegalArgumentException.class, () -> lookup(new YamlPipelineDefinitionParser().parse(
                yaml().replace("hostname: source.example", "datasource-id: saved\n  hostname: source.example")),
                new AtomicInteger(), new AtomicInteger()));
        PipelineDef overlapping = new YamlPipelineDefinitionParser().parse(yaml()
                .replace("hostname: sink.example", "hostname: source.example")
                .replace("warehouse.orders_copy", "shop.orders"));
        CliDataSourceLookupService lookup = lookup(overlapping, new AtomicInteger(), new AtomicInteger());
        assertThrows(IllegalArgumentException.class, () -> lookup.resolve(overlapping));
    }

    @Test
    void mysqlSnapshotIsAdmittedThroughTheSameConnectionMapping() {
        PipelineDef raw = new YamlPipelineDefinitionParser().parse(yaml().replaceFirst("type: jdbc-mysql", "type: mysql\n  scan.startup.mode: snapshot"));
        PipelineDef resolved = lookup(raw, new AtomicInteger(), new AtomicInteger()).resolve(raw);
        assertEquals("mysql", resolved.getSource().getType());
        assertEquals("snapshot", resolved.getSource().getConfig().toMap().get("scan.startup.mode"));
        assertEquals(RuntimeExecutionMode.BATCH, resolved.getConfig().get(PipelineOptions.PIPELINE_EXECUTION_RUNTIME_MODE));
    }

    @Test
    void lifecycleStartsAndWaitsForCompletion() throws Exception {
        FixtureExecution execution = new FixtureExecution();
        execution.result.complete(JobStatus.FINISHED);
        assertEquals(JobStatus.FINISHED, CliFrontend.execute(execution));
        assertEquals(0, execution.started.getCount());
        assertEquals(0, execution.cancellations.get());
    }

    @Test
    void interruptionWaitsForPhysicalTerminationAfterCancelAcknowledgment() throws Exception {
        FixtureExecution execution = new FixtureExecution();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                CliFrontend.execute(execution);
            } catch (Exception caught) {
                failure.set(caught);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        assertTrue(execution.started.await(5, TimeUnit.SECONDS));
        worker.interrupt();
        assertTrue(execution.cancelRequested.await(5, TimeUnit.SECONDS));
        assertTrue(worker.isAlive(), "A cancellation acknowledgment does not prove physical termination");
        execution.result.complete(JobStatus.CANCELED);
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertInstanceOf(InterruptedException.class, failure.get());
        assertTrue(interrupted.get());
        assertEquals(1, execution.cancellations.get());
    }

    @Test
    void failedCancellationStillWaitsForCompletionAndRetainsBothFailures() throws Exception {
        for (boolean completionFails : List.of(false, true)) {
            FixtureExecution execution = new FixtureExecution();
            IllegalStateException cancellationFailure = new IllegalStateException("Cancellation request failed");
            IllegalStateException completionFailure = new IllegalStateException("Execution completion failed");
            execution.cancellationResult = CompletableFuture.failedFuture(cancellationFailure);
            AtomicReference<Throwable> observed = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread worker = Thread.ofPlatform().start(() -> {
                try {
                    CliFrontend.execute(execution);
                } catch (Exception failure) {
                    observed.set(failure);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            try {
                assertTrue(execution.started.await(5, TimeUnit.SECONDS));
                worker.interrupt();
                assertTrue(execution.awaitedAfterCancellation.await(5, TimeUnit.SECONDS),
                        "Failed cancellation must still await physical completion");
                assertTrue(worker.isAlive());
                assertFalse(execution.result.isDone());
                if (completionFails) execution.result.completeExceptionally(completionFailure);
                else execution.result.complete(JobStatus.CANCELED);
                worker.join(5000);
                assertFalse(worker.isAlive());
                assertInstanceOf(InterruptedException.class, observed.get());
                assertTrue(interrupted.get());
                assertEquals(1, observed.get().getSuppressed().length);
                Throwable terminalFailure = observed.get().getSuppressed()[0];
                assertSame(completionFails ? completionFailure : cancellationFailure, terminalFailure.getCause());
                if (completionFails) {
                    assertEquals(1, terminalFailure.getSuppressed().length);
                    assertSame(cancellationFailure, terminalFailure.getSuppressed()[0].getCause());
                }
            } finally {
                execution.result.complete(JobStatus.CANCELED);
                worker.join(5000);
            }
        }
    }

    private static CliDataSourceLookupService lookup(PipelineDef raw, AtomicInteger opened, AtomicInteger closed) {
        return new CliDataSourceLookupService(raw, FACTORIES, "cli-test", "instance", connection -> {
            opened.incrementAndGet();
            boolean source = connection.host().equals("source.example");
            return new FixtureCatalog(source, closed);
        });
    }

    private static String yaml() {
        return """
                source:
                  type: jdbc-mysql
                  hostname: source.example
                  username: reader
                  password: source-secret
                  tables: shop.orders(_archive)?
                  tables.exclude: shop.orders_archive
                sink:
                  type: jdbc-mysql
                  hostname: sink.example
                  username: writer
                  password: sink-secret
                  write.mode: APPEND
                route:
                  - source-table: shop.orders
                    sink-table: warehouse.orders_copy
                pipeline:
                  execution.runtime-mode: BATCH
                  local-time-zone: UTC
                """;
    }

    private static final class FixtureCatalog implements JdbcCatalog {
        private final List<TableId> tables;
        private final AtomicInteger closed;

        private FixtureCatalog(boolean source, AtomicInteger closed) {
            tables = source ? List.of(SOURCE, new TableId("shop", null, "orders_archive"), new TableId("shop", null, "products"))
                    : List.of(TARGET);
            this.closed = closed;
        }

        @Override
        public List<String> listDatabases() { return tables.stream().map(TableId::catalog).distinct().toList(); }

        @Override
        public List<String> listSchemas(String database) { return List.of(); }

        @Override
        public List<TableId> listTables(String database, String schema) {
            return tables.stream().filter(table -> table.catalog().equals(database)).toList();
        }

        @Override
        public List<JdbcTableInfo> listTableInfos(String database, String schema, String keyword, Integer limit) {
            return listTables(database, schema).stream().map(table -> new JdbcTableInfo(table, "TABLE", null)).toList();
        }

        @Override
        public Optional<JdbcTableInfo> findTable(TableId tableId) {
            return tables.contains(tableId) ? Optional.of(new JdbcTableInfo(tableId, "TABLE", null)) : Optional.empty();
        }

        @Override
        public List<JdbcColumnInfo> getColumns(TableId tableId) {
            return List.of(new JdbcColumnInfo("id", "INT", Types.INTEGER, 10, 0, false, 1, true, 1,
                    "order identifier", false, false, false, null, null, false, "int"));
        }

        @Override
        public TableSchema getTable(TableId tableId) { return SCHEMA; }

        @Override
        public boolean tableExists(TableId tableId) { return tables.contains(tableId); }

        @Override
        public List<List<String>> getUniqueKeys(TableId tableId) { return List.of(List.of("id")); }

        @Override
        public void close() { closed.incrementAndGet(); }
    }

    private static final class FixtureExecution implements PipelineExecution {
        private final CompletableFuture<JobStatus> result = new CompletableFuture<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch cancelRequested = new CountDownLatch(1);
        private final CountDownLatch awaitedAfterCancellation = new CountDownLatch(1);
        private final AtomicInteger cancellations = new AtomicInteger();
        private CompletableFuture<Void> cancellationResult = CompletableFuture.completedFuture(null);

        @Override
        public String getJobId() { return "fixture"; }

        @Override
        public void start() { started.countDown(); }

        @Override
        public JobStatus getStatus() { return result.getNow(JobStatus.RUNNING); }

        @Override
        public CompletionStage<JobStatus> completion() {
            if (cancellations.get() > 0) awaitedAfterCancellation.countDown();
            return result;
        }

        @Override
        public CompletionStage<Void> cancel() {
            cancellations.incrementAndGet();
            cancelRequested.countDown();
            return cancellationResult;
        }

        @Override
        public OptionalInt getProgressPercent() { return OptionalInt.empty(); }

        @Override
        public Optional<String> getExecutionReport() { return Optional.empty(); }

        @Override
        public Optional<String> getExecutionSummary() { return Optional.empty(); }

        @Override
        public CompletionStage<Optional<String>> engineJobIdReady() { return CompletableFuture.completedFuture(Optional.empty()); }

        @Override
        public Optional<String> engineJobId() { return Optional.empty(); }
    }
}
