package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.enums.task.DefinitionErrorCode;
import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.composer.yakflow.EnvironmentOptions;
import io.yak.ops.composer.yakflow.ExecutionConfigurationFactory;
import io.yak.ops.composer.yakflow.YakFlowPipelineComposer;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.flow.runtime.checkpoint.LocalCompletedCheckpointStore;
import io.yak.ops.flow.runtime.checkpoint.SchemaManagerSnapshotSerializer;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.datasync.PipelineYamlConverter;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.TestNames;
import io.yak.ops.e2e.support.ApiClient;
import io.yak.ops.e2e.support.BackendTestBase;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealtimeSyncIT extends BackendTestBase {
    @TempDir
    Path engineState;

    @Test
    void regexDiscoveryUsesPublishedDefaultsAndKeepsInitialTableOverrides() throws Exception {
        Map<String, Object> parameters = dynamicParameters();
        Map<String, Object> source = section(parameters, "source");
        Map<String, Object> initial = new LinkedHashMap<>(tables(source).getFirst());
        initial.put("dml", Map.of("events", List.of("INSERT", "UPDATE")));
        source.put("tables", List.of(initial));
        parameters.put("source", source);
        String definition = fixture.create(parameters, true);
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("initial_override", List.of(List.of(1L, "one"), List.of(2L, "two")));
        awaitStreaming(instance);

        fixture.source.execute("DELETE FROM orders WHERE id=2");
        fixture.source.execute("INSERT INTO orders VALUES(3,'after-delete')");
        fixture.awaitTarget("initial_override", List.of(List.of(1L, "one"), List.of(2L, "two"), List.of(3L, "after-delete")));
        createSourceTable("orders_2", 10L, "new-table-snapshot");
        fixture.awaitTarget("dyn_orders_2", List.of(List.of(10L, "new-table-snapshot")));
        fixture.source.execute("DELETE FROM orders_2 WHERE id=10");
        fixture.source.execute("INSERT INTO orders_2 VALUES (11,'new-table-incremental')");
        fixture.awaitTarget("dyn_orders_2", List.of(List.of(11L, "new-table-incremental")));
        createSourceTable("orders_3", 12L, "third-table");
        fixture.awaitTarget("dyn_orders_3", List.of(List.of(12L, "third-table")));
        JsonNode saved = fixture.api.call("GET", "/api/v1/task-definitions/" + definition);
        assertEquals(1, saved.path("parameters").path("source").path("tables").size(),
                "Discovery belongs to runtime state, not mutable task parameters");
        assertEquals(1, saved.path("definitionVersion").asInt());
        fixture.api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
        fixture.awaitState(instance, "CANCELED");
        assertEquals(0, fixture.claimCount(instance));
    }

    @Test
    void fixedIdentityCheckpointRestoresDiscoveredTablesAndAdmitsAThirdTable() throws Exception {
        Map<String, Object> parameters = dynamicParameters();
        Map<String, Object> source = section(parameters, "source");
        source.put("datasourceId", "source");
        parameters.put("source", source);
        Map<String, Object> sink = section(parameters, "sink");
        sink.put("datasourceId", "target");
        parameters.put("sink", sink);
        PipelineDef definition = new YamlPipelineDefinitionParser()
                .parse(PipelineYamlConverter.convert(ReadonlyConfig.fromMap(parameters)));
        PipelineContext identity = new PipelineContext(fixture.api.workspace(), "dynamic-definition", 1, "dynamic-instance", true);
        EngineRun first = new EngineRun(definition, identity);
        first.start();
        try {
            first.awaitRows("initial_override", List.of(List.of(1L, "one"), List.of(2L, "two")));
            first.awaitStreaming();
            createSourceTable("orders_2", 10L, "checkpointed-snapshot");
            first.awaitRows("dyn_orders_2", List.of(List.of(10L, "checkpointed-snapshot")));
            long observed = first.report().path("checkpoints").path("completedId").asLong(-1);
            await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(200))
                    .until(() -> first.report().path("checkpoints").path("completedId").asLong(-1) > observed);
        } finally {
            first.close();
        }
        first.assertCheckpointContains(new TableId(fixture.sourceName, null, "orders_2"));

        // A recovery must replay the saved offset, not re-snapshot an already completed table.
        fixture.target.execute("UPDATE " + fixture.table("dyn_orders_2") + " SET name='checkpoint-preserved' WHERE id=10");
        fixture.source.execute("INSERT INTO orders_2 VALUES (11,'while-stopped')");
        EngineRun restored = new EngineRun(definition, identity);
        restored.start();
        try {
            restored.awaitRows("dyn_orders_2", List.of(List.of(10L, "checkpoint-preserved"), List.of(11L, "while-stopped")));
            assertTrue(restored.configuration.get(CheckpointingOptions.RESTORE_LATEST));
            restored.awaitStreaming();
            createSourceTable("orders_3", 12L, "after-recovery");
            restored.awaitRows("dyn_orders_3", List.of(List.of(12L, "after-recovery")));
            fixture.source.execute("UPDATE orders_3 SET name='third-incremental' WHERE id=12");
            restored.awaitRows("dyn_orders_3", List.of(List.of(12L, "third-incremental")));
        } finally {
            restored.close();
        }
    }

    @Test
    void publicationRejectsMysqlAliasWhoseFutureTargetTemplateWouldBeCapturedAgain() throws Exception {
        String aliasName = TestNames.unique("mysql_alias");
        fixture.api.call("POST", "/api/v1/data-source", Map.of("name", aliasName, "dbType", "MYSQL",
                "environment", "TEST", "connectionParams", Map.of("host", fixture.source.host(),
                        "port", fixture.source.port(), "database", fixture.sourceName,
                        "username", fixture.source.username(), "password", fixture.source.password(),
                        "properties", Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"))));
        String alias = null;
        for (JsonNode row : fixture.api.call("POST", "/api/v1/data-source/page", Map.of("pageNo", 1, "pageSize", 100))
                .path("bizData")) {
            if (aliasName.equals(row.path("name").asText())) alias = row.path("id").asText();
        }
        assertTrue(alias != null);
        Map<String, Object> parameters = dynamicParameters();
        Map<String, Object> source = section(parameters, "source");
        assertFalse(alias.equals(source.get("datasourceId")), "Distinct saved references still share one MySQL UUID");
        source.put("selection", Map.of("mode", "REGEX", "databasePattern", "^" + fixture.sourceName + "$",
                "tablePattern", "^orders.*$"));
        parameters.put("source", source);
        Map<String, Object> sink = section(parameters, "sink");
        sink.put("type", "jdbc-mysql");
        sink.put("datasourceId", alias);
        sink.put("tables", List.of(Map.of("database", fixture.sourceName, "table", "archive_orders",
                "write", Map.of("mode", "UPSERT"))));
        sink.put("mapping", Map.of("database", fixture.sourceName, "table", "${table}_copy",
                "write", Map.of("mode", "UPSERT")));
        parameters.put("sink", sink);
        parameters.put("route", List.of(Map.of("source-table", fixture.sourceName + ".orders",
                "sink-table", fixture.sourceName + ".archive_orders")));
        String definition = fixture.create(parameters, false);
        fixture.api.expectError("POST", "/api/v1/task-definitions/" + definition + "/publish",
                fixture.api.publicationRequest(definition), 400, DefinitionErrorCode.PUBLICATION_INVALID.getCode());
        assertFalse(fixture.api.call("GET", "/api/v1/task-definitions/" + definition).hasNonNull("publishedVersion"));
        try (Connection connection = fixture.source.connect();
                var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='archive_orders'")) {
            statement.setString(1, fixture.sourceName);
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertEquals(0, rows.getInt(1), "Publication checks must not create target tables");
            }
        }
    }

    private Map<String, Object> dynamicParameters() {
        Map<String, Object> route = new LinkedHashMap<>(fixture.route("orders", "initial_override"));
        route.put("source", Map.of("database", fixture.sourceName, "table", "orders"));
        Map<String, Object> parameters = fixture.parameters("DATABASE_REALTIME",
                List.of("SCHEMA", "FULL", "INCREMENTAL"), List.of(route));
        Map<String, Object> source = section(parameters, "source");
        source.put("selection", Map.of("mode", "REGEX", "databasePattern", "^(?:" + fixture.sourceName + ")$",
                "tablePattern", "^orders(?:_[23])?$"));
        parameters.put("source", source);
        fixture.setSchemaEvolution(parameters, true, false);
        Map<String, Object> sink = section(parameters, "sink");
        sink.put("mapping", Map.of("schema", fixture.targetSchema, "table", "dyn_${table}",
                "write", Map.of("mode", "UPSERT")));
        parameters.put("sink", sink);
        parameters.put("pipeline", Map.of("execution.runtime-mode", "STREAMING", "pipeline.table-scope", "DATABASE", "parallelism", 2));
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parameters, String name) {
        return new LinkedHashMap<>((Map<String, Object>) parameters.get(name));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tables(Map<String, Object> endpoint) {
        return (List<Map<String, Object>>) endpoint.get("tables");
    }

    private void createSourceTable(String table, long id, String name) throws Exception {
        assertTrue(Set.of("orders_2", "orders_3").contains(table));
        fixture.source.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        try (Connection connection = fixture.source.connect();
                var statement = connection.prepareStatement("INSERT INTO " + table + " VALUES (?,?)")) {
            statement.setLong(1, id);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    /** Exercises durable engine recovery without claiming the HTTP process automatically resumes uncertain instances. */
    private final class EngineRun implements AutoCloseable {
        private final PipelineExecution execution;
        private volatile Configuration configuration;
        private volatile StreamGraph graph;

        private EngineRun(PipelineDef definition, PipelineContext identity) {
            ReadonlyConfig settings = EnvironmentOptions.defaults();
            DataSourceLookupService lookup = new EngineLookup();
            var composer = new YakFlowPipelineComposer(lookup, new FactoryUtil(), settings,
                    new ExecutionConfigurationFactory(settings, engineState),
                    Map.of("mysql", ReadonlyConfig.fromMap(Map.of("server-id", "31700-31716",
                            "topic-prefix", "dynamic-" + fixture.sourceName))), Map.of(),
                    (pipeline, config) -> {
                        graph = (StreamGraph) pipeline;
                        configuration = config;
                        return new EmbeddedPipelineExecutor().execute(pipeline, config);
                    }, new IncrementalBatchPreparation(lookup, (task, proposed) -> {
                        throw new AssertionError("CDC must not use offline cursor storage");
                    }));
            execution = composer.compose(definition, composer.plan(definition, identity), identity);
        }

        private void start() {
            execution.start();
        }

        private JsonNode report() throws Exception {
            if (Set.of(JobStatus.FAILED, JobStatus.CANCELED, JobStatus.FINISHED).contains(execution.getStatus())) {
                throw new IllegalStateException("Engine terminated before its expected observation: " + execution.getStatus());
            }
            return ApiClient.JSON.readTree(execution.getExecutionReport().orElse("{}"));
        }

        private void awaitStreaming() {
            await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(200))
                    .until(() -> "STREAMING".equals(report().path("phase").asText()));
        }

        private void awaitRows(String table, List<? extends List<?>> rows) {
            await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
                report();
                fixture.assertTarget(table, rows);
            });
        }

        private void assertCheckpointContains(TableId source) throws Exception {
            Path directory = Path.of(configuration.get(CheckpointingOptions.STATE_DIRECTORY));
            try (var store = new LocalCompletedCheckpointStore(directory)) {
                String signature = LocalCompletedCheckpointStore.graphSignature(graph,
                        configuration.get(io.yak.ops.core.configuration.PipelineOptions.MAX_PARALLELISM),
                        configuration.get(CheckpointingOptions.DEFINITION_IDENTITY));
                var snapshot = store.loadLatest(signature).orElseThrow();
                var schemas = new SchemaManagerSnapshotSerializer().deserialize(
                        snapshot.schemaManagerState().version(), snapshot.schemaManagerState().bytes());
                assertTrue(schemas.tables().stream().anyMatch(state -> state.sourceTable().equals(source)),
                        "The durable schema checkpoint must contain the dynamically admitted route");
            }
        }

        @Override
        public void close() throws Exception {
            if (!execution.completion().toCompletableFuture().isDone()) {
                execution.cancel().toCompletableFuture().get(30, TimeUnit.SECONDS);
            }
            assertEquals(JobStatus.CANCELED, execution.completion().toCompletableFuture().get(30, TimeUnit.SECONDS));
        }
    }

    private final class EngineLookup implements DataSourceLookupService {
        @Override
        public DataSourceInfo dataSource(String workspace, String id) {
            assertEquals(fixture.api.workspace(), workspace);
            return switch (id) {
                case "source" -> new DataSourceInfo("MYSQL", fixture.sourceName, null, 1);
                case "target" -> new DataSourceInfo("POSTGRE_SQL", "yak_it", null, 1);
                default -> throw new IllegalArgumentException("Unknown test datasource");
            };
        }

        @Override
        public DataSourceTable table(String workspace, String id, String database, String schema, String table) {
            return findTable(workspace, id, database, schema, table).orElseThrow();
        }

        @Override
        public Optional<DataSourceTable> findTable(String workspace, String id, String database, String schema, String table) {
            DataSourceInfo info = dataSource(workspace, id);
            TableId selected = info.resolveTable(database, schema, table);
            try (JdbcCatalog catalog = JdbcCatalogFactory.create(new JdbcConnectionOptions(connectionOptions(workspace, id)))) {
                if (catalog.findTable(selected).isEmpty()) return Optional.empty();
                return Optional.of(new DataSourceTable(info.type(), selected, catalog.getTable(selected), 1));
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException("Cannot inspect the test table", failure);
            }
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String id) {
            dataSource(workspace, id);
            DatabaseMetadata connection = id.equals("source") ? fixture.source : fixture.target;
            return new JdbcConnectionOptions(connection.url(), connection.username(), connection.password()).configuration();
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String id, long revision) {
            assertEquals(1L, revision);
            return connectionOptions(workspace, id);
        }
    }
    @Test
    void transformProjectionAndFilterApplySnapshotAndBinlogBoundaryCrossings() throws Exception {
        fixture.source.execute("ALTER TABLE orders ADD COLUMN eligible INT NOT NULL DEFAULT 0");
        fixture.source.execute("UPDATE orders SET eligible=1 WHERE id=1");
        // Flink CDC infers a nullable STRING for UPPER, even from a non-null VARCHAR input.
        fixture.target.execute("CREATE TABLE " + fixture.table("filtered_orders")
                + " (id BIGINT PRIMARY KEY, name TEXT)");
        Map<String, Object> route = new LinkedHashMap<>(fixture.route("orders", "filtered_orders"));
        route.put("source", Map.of("database", fixture.sourceName, "table", "orders"));
        Map<String, Object> parameters = fixture.parameters(
                "SINGLE_TABLE_REALTIME", List.of("FULL", "INCREMENTAL"), List.of(route));
        parameters.put("transform", List.of(Map.of(
                "source-table", fixture.sourceName + ".orders",
                "projection", "id, UPPER(name) AS name",
                "filter", "eligible = 1")));

        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("filtered_orders", List.of(List.of(1L, "ONE")));
        awaitStreaming(instance);

        // Crossing into the filter must turn an UPDATE into a target INSERT.
        fixture.source.execute("UPDATE orders SET eligible=1,name='two-visible' WHERE id=2");
        fixture.awaitTarget("filtered_orders", List.of(List.of(1L, "ONE"), List.of(2L, "TWO-VISIBLE")));
        // Crossing out must retract the projected before-image from the target.
        fixture.source.execute("UPDATE orders SET eligible=0 WHERE id=1");
        fixture.awaitTarget("filtered_orders", List.of(List.of(2L, "TWO-VISIBLE")));
        fixture.source.execute("UPDATE orders SET name='updated' WHERE id=2");
        fixture.awaitTarget("filtered_orders", List.of(List.of(2L, "UPDATED")));
    }

    @Test
    void parallelChangelogKeepsAlternateKeyChangesAndReuseOrdered() throws Exception {
        fixture.source.execute("ALTER TABLE orders ADD COLUMN code INT NULL");
        fixture.source.execute("UPDATE orders SET code=id");
        fixture.source.execute("ALTER TABLE orders MODIFY code INT NOT NULL, ADD UNIQUE INDEX unique_code(code)");
        fixture.target.execute("CREATE TABLE " + fixture.table("keyed_orders")
                + " (id BIGINT NOT NULL, code INT PRIMARY KEY)");
        Map<String, Object> route = new LinkedHashMap<>(fixture.route("orders", "keyed_orders"));
        route.put("source", Map.of("database", fixture.sourceName, "table", "orders"));
        Map<String, Object> parameters = fixture.parameters(
                "SINGLE_TABLE_REALTIME", List.of("FULL", "INCREMENTAL"), List.of(route));
        parameters.put("transform", List.of(Map.of(
                "source-table", fixture.sourceName + ".orders",
                "projection", "id, code",
                "primary-keys", "code")));
        parameters.put("pipeline", Map.of("execution.runtime-mode", "STREAMING", "parallelism", 2));

        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");
        List<List<Object>> expected = new ArrayList<>();
        expected.add(List.of(1L, 1));
        expected.add(List.of(2L, 2));
        awaitKeyedRows(expected);
        awaitStreaming(instance);

        // Both key-change directions occur in one committed binlog transaction.
        // The physical source key remains unchanged throughout each UPDATE.
        try (Connection connection = fixture.source.connect();
                var insert = connection.prepareStatement("INSERT INTO orders (id,name,code) VALUES (?,?,?)");
                var update = connection.prepareStatement("UPDATE orders SET code=? WHERE id=?")) {
            connection.setAutoCommit(false);
            for (int id = 3; id < 35; id++) {
                int initialCode = 1000 + id * 2 + id % 2;
                int finalCode = 1000 + id * 2 + 1 - id % 2;
                insert.setLong(1, id);
                insert.setString(2, "row-" + id);
                insert.setInt(3, initialCode);
                insert.executeUpdate();
                update.setInt(1, finalCode);
                update.setLong(2, id);
                update.executeUpdate();
                expected.add(List.of((long) id, finalCode));
            }
            connection.commit();
        }
        awaitKeyedRows(expected);

        // A different physical row may reuse a released output key immediately.
        try (Connection connection = fixture.source.connect();
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("UPDATE orders SET code=8001 WHERE id=3");
            statement.executeUpdate("INSERT INTO orders (id,name,code) VALUES (99,'reused-key',1006)");
            connection.commit();
        }
        expected.set(2, List.of(3L, 8001));
        expected.add(List.of(99L, 1006));
        awaitKeyedRows(expected);
        fixture.api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
        fixture.awaitState(instance, "CANCELED");
        assertEquals(0, fixture.claimCount(instance));
    }

    private void awaitStreaming(String instance) {
        await().alias("Pipeline enters continuous binlog reading")
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    JsonNode current = fixture.api.call("GET", "/api/v1/task-instances/" + instance);
                    String status = current.path("status").asText();
                    if (Set.of("FINISHED", "FAILED", "CANCELED").contains(status)) {
                        throw new IllegalStateException("Pipeline terminated before streaming: " + status);
                    }
                    String report = current.path("executionReport").asText("");
                    return !report.isBlank()
                            && "STREAMING".equals(ApiClient.JSON.readTree(report).path("phase").asText());
                });
    }

    private void awaitKeyedRows(List<List<Object>> expected) {
        await().alias("Projected alternate keys have no stale target rows")
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    List<List<Object>> actual = new ArrayList<>();
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.createStatement();
                            var rows = statement.executeQuery(
                                    "SELECT id,code FROM " + fixture.table("keyed_orders") + " ORDER BY id,code")) {
                        while (rows.next()) actual.add(List.of(rows.getLong(1), rows.getInt(2)));
                    }
                    assertEquals(expected, actual);
                });
    }

    @Test
    void keylessMysqlXaSnapshotAndBinlogDoNotRequireReceiptTable() throws Exception {
        String instance = fixture.submit(fixture.keylessXaRealtime());
        fixture.awaitState(instance, "RUNNING");
        awaitRows(Map.of("1:10.00", 2L, "2:NULL", 1L));
        fixture.source.execute("UPDATE keyless_source SET amount=11.00 WHERE bucket=2");
        fixture.source.execute(
                "DELETE FROM keyless_source WHERE bucket=1 AND amount=10.00 LIMIT 1");
        fixture.source.execute("INSERT INTO keyless_source VALUES (3,30.00)");
        awaitRows(Map.of("1:10.00", 1L, "2:11.00", 1L, "3:30.00", 1L));
        try (Connection connection = fixture.source.connect();
                var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA=? AND TABLE_NAME='yak_flow_cdc_receipts'")) {
            statement.setString(1, fixture.sourceName);
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertEquals(0, rows.getLong(1), "XA must not require a Receipt table");
            }
        }
        fixture.api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
        fixture.awaitState(instance, "CANCELED");
    }

    private void awaitRows(Map<String, Long> expected) {
        await().alias("MySQL keyless XA rows")
                .atMost(Duration.ofSeconds(150))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertEquals(expected, keylessRows()));
    }

    private Map<String, Long> keylessRows() throws Exception {
        Map<String, Long> values = new LinkedHashMap<>();
        try (Connection connection = fixture.source.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT bucket,amount,COUNT(*) FROM keyless_target "
                                + "GROUP BY bucket,amount")) {
            while (rows.next()) {
                var amount = rows.getBigDecimal(2);
                values.put(rows.getLong(1) + ":" + (amount == null ? "NULL" : amount.toPlainString()),
                        rows.getLong(3));
            }
        }
        return values;
    }

    @Test
    void snapshotContinuesWithBinlogAndCancellationStopsTheJob() throws Exception {
        String instance = fixture.submit(fixture.realtime());
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        fixture.source.execute("UPDATE orders SET name='changed' WHERE id=1");
        fixture.source.execute("DELETE FROM orders WHERE id=2");
        fixture.source.execute("INSERT INTO orders VALUES(3,'three')");
        fixture.awaitTarget("orders", List.of(List.of(1L, "changed"), List.of(3L, "three")));
        fixture.api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
        fixture.awaitState(instance, "CANCELED");
        assertEquals(0, fixture.claimCount(instance));
    }
}
