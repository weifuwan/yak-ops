package io.yak.ops.e2e.support;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.JdbcAssertions;
import io.yak.ops.testutils.TestNames;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates public API entities and disposable data owned by exactly one test scenario. */
public final class DataSyncFixture implements AutoCloseable {
    public final BootProcess app;
    public ApiClient api;
    public DatabaseMetadata source;
    public final DatabaseMetadata target;
    public final String sourceName = TestNames.unique("source");
    public final String targetSchema = TestNames.unique("target");
    private String sourceId;
    private String targetId;
    private boolean sourceCreated;
    private boolean targetCreated;
    private final List<String> instances = new ArrayList<>();
    private final List<String> schedules = new ArrayList<>();
    private static final Set<String> TERMINAL = Set.of("FINISHED", "FAILED", "CANCELED");

    public DataSyncFixture(BootProcess app, DatabaseMetadata target) {
        this.app = app;
        this.target = target;
    }

    public void start() throws Exception {
        api = app.login();
        api.workspace(api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("workspace")))
                .path("id")
                .asText());
        app.server().execute("CREATE DATABASE " + sourceName);
        sourceCreated = true;
        source = BootProcess.mysqlDatabase(app.server(), sourceName);
        target.execute("CREATE SCHEMA " + targetSchema);
        targetCreated = true;
        source.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        source.execute("INSERT INTO orders VALUES (1,'one'),(2,'two')");
        sourceId = datasource("MYSQL", sourceName, source);
        targetId = datasource("POSTGRE_SQL", "yak_it", target);
    }

    private String datasource(String type, String database, DatabaseMetadata connection) {
        String name = TestNames.unique("datasource");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("host", connection.host());
        values.put("port", connection.port());
        values.put("database", database);
        values.put("username", connection.username());
        values.put("password", connection.password());
        if (type.equals("MYSQL"))
            values.put("properties", Map.of("allowPublicKeyRetrieval", "true", "useSSL", "false"));
        api.call(
                "POST",
                "/api/v1/data-source",
                Map.of("name", name, "dbType", type, "environment", "TEST", "connectionParams", values));
        for (JsonNode row : api.call("POST", "/api/v1/data-source/page", Map.of("pageNo", 1, "pageSize", 100))
                .path("bizData")) {
            if (row.path("name").asText().equals(name)) return row.path("id").asText();
        }
        throw new AssertionError("Created datasource was not returned in its Workspace");
    }

    public void createTarget(String name) throws Exception {
        target.execute("CREATE TABLE " + table(name) + " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
    }

    public String table(String name) {
        if (!name.matches("[a-z][a-z0-9_]{0,30}")) throw new IllegalArgumentException("Unsafe test table");
        return targetSchema + "." + name;
    }

    public Map<String, Object> route(String from, String to) {
        return Map.of(
                "source",
                Map.of("table", from),
                "target",
                Map.of("schema", targetSchema, "table", to),
                "writeMode",
                "UPSERT");
    }

    /** Produces mutable test scenario inputs. Submission uses only the V6 YAML execution contract. */
    public Map<String, Object> parameters(String solution, List<String> steps, List<Map<String, Object>> routes) {
        boolean realtime = solution.endsWith("_REALTIME");
        List<Map<String, Object>> sourceTables = new ArrayList<>();
        List<Map<String, Object>> sinkTables = new ArrayList<>();
        List<Map<String, Object>> tableRoutes = new ArrayList<>();
        for (Map<String, Object> route : routes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> from = (Map<String, Object>) route.get("source");
            @SuppressWarnings("unchecked")
            Map<String, Object> to = (Map<String, Object>) route.get("target");
            Map<String, Object> input = new LinkedHashMap<>(from);
            Object chunkKey = input.remove("chunkKeyColumn");
            input.remove("xa");
            if (chunkKey != null) input.put("snapshot", Map.of("chunkKeyColumn", chunkKey));
            if (route.containsKey("incremental")) input.put("incremental", route.get("incremental"));
            sourceTables.add(input);
            Map<String, Object> write = new LinkedHashMap<>();
            write.put("mode", route.get("writeMode"));
            if (to.containsKey("primaryKeys")) write.put("primaryKeys", to.get("primaryKeys"));
            if ("RETRACT".equals(route.get("writeMode"))) {
                write.put("transaction", Map.of("mode", "CHECKPOINT_XA"));
            }
            Map<String, Object> output = new LinkedHashMap<>(to);
            output.remove("primaryKeys");
            output.put("write", write);
            sinkTables.add(output);
            tableRoutes.add(Map.of("source-table", path(input), "sink-table", path(output)));
        }
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", realtime ? "MYSQL_CDC" : "JDBC");
        source.put("datasourceId", sourceId);
        source.put("tables", sourceTables);
        if (realtime) {
            source.put("startup", Map.of("mode",
                    !steps.contains("FULL") ? "LATEST_OFFSET"
                            : steps.contains("INCREMENTAL") ? "INITIAL" : "SNAPSHOT"));
        } else {
            source.put("syncMode", steps.contains("FULL") && steps.contains("INCREMENTAL")
                    ? "FULL_AND_INCREMENTAL"
                    : steps.contains("INCREMENTAL") ? "INCREMENTAL" : "FULL");
        }
        Map<String, Object> sink = new LinkedHashMap<>();
        sink.put("type", "JDBC");
        sink.put("datasourceId", targetId);
        sink.put("schemaPolicy", Map.of(
                "initial", steps.contains("SCHEMA") ? "CREATE_OR_ALTER" : "VERIFY",
                "evolution", Map.of("enabled", false, "allowDropColumns", false)));
        sink.put("tables", sinkTables);
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("source", source);
        definition.put("sink", sink);
        definition.put("route", tableRoutes);
        definition.put("transform", List.of());
        definition.put("pipeline", Map.of("execution.runtime-mode",
                realtime && steps.contains("INCREMENTAL") ? "STREAMING" : "BATCH"));
        return definition;
    }

    private static String path(Map<String, Object> table) {
        List<String> names = new ArrayList<>();
        for (String field : List.of("database", "schema", "table")) {
            if (table.get(field) != null) names.add(table.get(field).toString());
        }
        return String.join(".", names);
    }

    /** Enables runtime target schema coordination in one Sink-owned location. */
    public void setSchemaEvolution(Map<String, Object> parameters, boolean enabled, boolean allowDrop) {
        @SuppressWarnings("unchecked")
        Map<String, Object> saved = (Map<String, Object>) parameters.get("sink");
        var sink = new LinkedHashMap<>(saved);
        sink.put("schemaPolicy", Map.of(
                "initial", "CREATE_OR_ALTER",
                "evolution", Map.of("enabled", enabled, "allowDropColumns", allowDrop)));
        parameters.put("sink", sink);
    }

    /**
     * Convert disposable scenario fixtures to the public V6 YAML representation at the API boundary.
     * Production never invokes a V5 migration adapter; this helper only keeps E2E scenario builders readable.
     */
    private static Map<String, Object> yamlParameters(Map<String, Object> scenario) {
        @SuppressWarnings("unchecked")
        Map<String, Object> rawSource = (Map<String, Object>) scenario.get("source");
        @SuppressWarnings("unchecked")
        Map<String, Object> rawSink = (Map<String, Object>) scenario.get("sink");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) rawSource.get("tables");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sinks = (List<Map<String, Object>>) rawSink.get("tables");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes = (List<Map<String, Object>>) scenario.get("route");
        @SuppressWarnings("unchecked")
        Map<String, Object> productPipeline = (Map<String, Object>) scenario.get("pipeline");

        String type = rawSource.get("type").toString();
        Map<String, String> source = new LinkedHashMap<>();
        source.put("type", "MYSQL_CDC".equals(type) ? "mysql" : "jdbc");
        source.put("datasource-id", rawSource.get("datasourceId").toString());
        source.put("tables", String.join(",", sources.stream().map(DataSyncFixture::path).toList()));
        source.put("yak.namespace-kind", sources.stream().anyMatch(table -> table.containsKey("database"))
                ? "DATABASE" : "SCHEMA");
        if ("MYSQL_CDC".equals(type)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> startup = (Map<String, Object>) rawSource.getOrDefault(
                    "startup", Map.of("mode", "INITIAL"));
            source.put("scan.startup.mode", switch (startup.get("mode").toString()) {
                case "INITIAL" -> "initial";
                case "SNAPSHOT" -> "snapshot";
                case "EARLIEST_OFFSET" -> "earliest-offset";
                case "LATEST_OFFSET" -> "latest-offset";
                case "SPECIFIC_OFFSETS" -> "specific-offset";
                case "TIMESTAMP" -> "timestamp";
                default -> throw new IllegalArgumentException("Unsupported test CDC startup");
            });
            for (Map.Entry<String, String> option : Map.of(
                            "file", "scan.startup.specific-offset.file",
                            "pos", "scan.startup.specific-offset.pos",
                            "gtidSet", "scan.startup.specific-offset.gtid-set",
                            "timestampMillis", "scan.startup.timestamp-millis",
                            "skipEvents", "scan.startup.specific-offset.skip-events",
                            "skipRows", "scan.startup.specific-offset.skip-rows")
                    .entrySet()) {
                if (startup.containsKey(option.getKey())) {
                    source.put(option.getValue(), startup.get(option.getKey()).toString());
                }
            }
        } else {
            source.put("yak.sync.mode", rawSource.getOrDefault("syncMode", "FULL").toString());
        }
        for (Map<String, Object> table : sources) {
            String prefix = "yak.table." + path(table) + ".";
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = (Map<String, Object>) table.get("snapshot");
            if (snapshot != null && snapshot.containsKey("chunkKeyColumn")) {
                source.put(prefix + "chunk-key-column", snapshot.get("chunkKeyColumn").toString());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> incremental = (Map<String, Object>) table.get("incremental");
            if (incremental != null) {
                for (Map.Entry<String, String> option : Map.of(
                                "mode", "mode",
                                "column", "column",
                                "initialValue", "initial-value",
                                "delaySeconds", "delay-seconds",
                                "lookbackSeconds", "lookback-seconds")
                        .entrySet()) {
                    if (incremental.containsKey(option.getKey())) {
                        source.put(prefix + "incremental." + option.getValue(),
                                incremental.get(option.getKey()).toString());
                    }
                }
            }
        }

        Map<String, String> sink = new LinkedHashMap<>();
        sink.put("type", "jdbc");
        sink.put("datasource-id", rawSink.get("datasourceId").toString());
        sink.put("yak.namespace-kind", sinks.stream().anyMatch(table -> table.containsKey("database"))
                ? "DATABASE" : "SCHEMA");
        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) rawSink.get("schemaPolicy");
        @SuppressWarnings("unchecked")
        Map<String, Object> evolution = (Map<String, Object>) policy.get("evolution");
        sink.put("yak.schema.initial", policy.get("initial").toString());
        sink.put("yak.schema.evolution.enabled", evolution.get("enabled").toString());
        sink.put("yak.schema.evolution.allow-drop-columns", evolution.get("allowDropColumns").toString());
        for (Map<String, Object> table : sinks) {
            @SuppressWarnings("unchecked")
            Map<String, Object> write = (Map<String, Object>) table.get("write");
            String prefix = "yak.table." + path(table) + ".";
            sink.put(prefix + "write.mode", write.get("mode").toString());
            if (write.containsKey("primaryKeys")) {
                @SuppressWarnings("unchecked")
                List<String> keys = (List<String>) write.get("primaryKeys");
                sink.put(prefix + "write.primary-keys", String.join(",", keys));
            }
            if (write.containsKey("transaction")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> transaction = (Map<String, Object>) write.get("transaction");
                sink.put(prefix + "write.transaction.mode", transaction.get("mode").toString());
            }
        }
        Map<String, String> pipeline = new LinkedHashMap<>();
        pipeline.put("execution.runtime-mode", productPipeline.get("execution.runtime-mode").toString());
        pipeline.put("yak.table-scope", productPipeline.containsKey("pipeline.table-scope")
                ? productPipeline.get("pipeline.table-scope").toString()
                : routes.size() == 1 ? "SINGLE" : "DATABASE");
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("source", source);
        definition.put("sink", sink);
        definition.put("route", routes);
        definition.put("pipeline", pipeline);
        try {
            return Map.of("pipelineYaml", new YAMLMapper().writeValueAsString(definition));
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Failed to create V6 E2E Pipeline YAML", failure);
        }
    }

    public String create(Map<String, Object> parameters, boolean publish) {
        String definition = api.call(
                        "POST",
                        "/api/v1/task-definitions",
                        Map.of(
                                "name",
                                TestNames.unique("task"),
                                "taskType",
                                "DATA_SYNC",
                                "debug",
                                true,
                                "parameters",
                                yamlParameters(parameters)))
                .path("id")
                .asText();
        if (publish) api.call("POST", "/api/v1/task-definitions/" + definition + "/publish");
        return definition;
    }

    public String batch() throws Exception {
        createTarget("orders");
        return create(parameters("SINGLE_TABLE_OFFLINE", List.of("FULL"), List.of(route("orders", "orders"))), true);
    }

    public String realtime() throws Exception {
        createTarget("orders");
        return create(
                parameters("SINGLE_TABLE_REALTIME", List.of("FULL", "INCREMENTAL"), List.of(route("orders", "orders"))),
                true);
    }

    /** Runs a real MySQL-to-MySQL keyless XA task without creating any Receipt table. */
    public String keylessXaRealtime() throws Exception {
        source.execute("CREATE TABLE keyless_source "
                + "(bucket BIGINT NOT NULL, amount DECIMAL(14,2) NULL) ENGINE=InnoDB");
        source.execute("CREATE TABLE keyless_target "
                + "(bucket BIGINT NOT NULL, amount DECIMAL(14,2) NULL) ENGINE=InnoDB");
        source.execute("INSERT INTO keyless_source VALUES (1,10.00),(1,10.00),(2,NULL)");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("source", Map.of(
                "type", "MYSQL_CDC", "datasourceId", sourceId,
                "startup", Map.of("mode", "INITIAL"),
                "tables", List.of(Map.of("table", "keyless_source",
                        "snapshot", Map.of("chunkKeyColumn", "bucket")))));
        parameters.put("sink", Map.of(
                "type", "JDBC", "datasourceId", sourceId,
                "schemaPolicy", Map.of("initial", "VERIFY",
                        "evolution", Map.of("enabled", false, "allowDropColumns", false)),
                "tables", List.of(Map.of("table", "keyless_target",
                        "write", Map.of("mode", "RETRACT",
                                "transaction", Map.of("mode", "CHECKPOINT_XA"))))));
        parameters.put("route", List.of(Map.of(
                "source-table", "keyless_source", "sink-table", "keyless_target")));
        parameters.put("transform", List.of());
        parameters.put("pipeline", Map.of("execution.runtime-mode", "STREAMING"));
        return create(parameters, true);
    }

    public String submit(String definition) {
        String instance = api.submit(definition).path("instanceId").asText();
        track(instance);
        return instance;
    }

    public void track(String instance) {
        if (!instances.contains(instance)) instances.add(instance);
    }

    public JsonNode awaitState(String instance, String expected) {
        JsonNode[] last = new JsonNode[1];
        await().alias("Task " + instance + " -> " + expected)
                .atMost(Duration.ofSeconds(180))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    last[0] = api.call("GET", "/api/v1/task-instances/" + instance);
                    String status = last[0].path("status").asText();
                    Files.writeString(app.directory().resolve("instance-" + instance + ".json"), last[0].toString());
                    if (TERMINAL.contains(status) && !status.equals(expected)) {
                        // Retain the masked task log beside the failing Instance snapshot.
                        // Never print raw database connections or Task parameters into CI output.
                        Path failureLog = app.directory().resolve("instance-" + instance + "-failure.log");
                        try {
                            Files.writeString(failureLog,
                                    api.download("/api/v1/task-instances/" + instance + "/logs/download"));
                        } catch (Exception unavailable) {
                            Files.writeString(failureLog, "Task log unavailable: "
                                    + unavailable.getClass().getSimpleName());
                        }
                        throw new IllegalStateException(
                                "Task ended in " + status + ", expected " + expected
                                        + "; failureCode=" + last[0].path("failureCode").asText("")
                                        + "; diagnostics=" + failureLog);
                    }
                    return status.equals(expected);
                });
        return last[0];
    }

    public void assertTarget(String name, List<? extends List<?>> expected) throws Exception {
        List<List<Object>> actual = new ArrayList<>();
        try (Connection connection = target.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT id,name FROM " + table(name))) {
            while (rows.next()) actual.add(Arrays.asList(rows.getLong(1), rows.getString(2)));
        }
        JdbcAssertions.assertRows(expected, actual);
    }

    public void awaitTarget(String name, List<? extends List<?>> expected) {
        await().alias("Target " + table(name))
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertTarget(name, expected));
    }

    public long claimCount(String instance) throws Exception {
        try (Connection connection = app.metadata().connect();
                var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM yak_ops_task_group_queue WHERE task_id=?")) {
            statement.setString(1, instance);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    public String schedule(String definition) {
        JsonNode schedule = api.call(
                "POST",
                "/api/v1/schedules",
                Map.of(
                        "targetType",
                        "TASK",
                        "targetId",
                        definition,
                        "cronExpression",
                        "0/5 * * * * ?",
                        "timeZone",
                        "Asia/Shanghai"));
        String id = schedule.path("id").asText();
        schedules.add(id);
        api.call(
                "POST",
                "/api/v1/schedules/" + id + "/enable",
                Map.of("revision", schedule.path("revision").asLong()));
        return id;
    }

    public void stopSchedule(String id) {
        JsonNode state = api.call("GET", "/api/v1/schedules/" + id);
        api.call(
                "POST",
                "/api/v1/schedules/" + id + "/disable",
                Map.of("revision", state.path("revision").asLong()));
        schedules.remove(id);
    }

    @Override
    public void close() throws Exception {
        if (api != null && api.workspace() != null) {
            for (String schedule : List.copyOf(schedules)) stopSchedule(schedule);
            // Include scheduled instances that were not returned by a manual submit call.
            JsonNode page = api.call("POST", "/api/v1/task-instances/page", Map.of("pageNo", 1, "pageSize", 200));
            for (JsonNode row : page.path("bizData")) track(row.path("id").asText());
            for (String instance : instances) {
                String status = api.call("GET", "/api/v1/task-instances/" + instance)
                        .path("status")
                        .asText();
                if (status.equals("RECONCILING")) continue;
                if (!TERMINAL.contains(status)) {
                    api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
                    await().alias("Physical task stopped before fixture cleanup")
                            .atMost(Duration.ofSeconds(60))
                            .pollInterval(Duration.ofMillis(200))
                            .until(() -> TERMINAL.contains(api.call("GET", "/api/v1/task-instances/" + instance)
                                    .path("status")
                                    .asText()));
                }
            }
        }
        // Dropping data is safe only after every ordinary live task has stopped.
        if (targetCreated) {
            target.execute("DROP SCHEMA " + targetSchema + " CASCADE");
            targetCreated = false;
        }
        if (sourceCreated) {
            app.server().execute("DROP DATABASE " + sourceName);
            sourceCreated = false;
        }
    }
}
