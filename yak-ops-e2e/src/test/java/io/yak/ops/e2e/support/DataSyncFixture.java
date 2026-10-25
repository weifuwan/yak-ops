package io.yak.ops.e2e.support;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
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

    /** Builds the current public Source/Sink configuration from logical E2E table routes. */
    public Map<String, Object> parameters(String solution, List<String> steps, List<Map<String, Object>> routes) {
        List<Map<String, Object>> sourceTables = new ArrayList<>();
        List<Map<String, Object>> sinkTables = new ArrayList<>();
        for (int index = 0; index < routes.size(); index++) {
            var route = routes.get(index);
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) route.get("source");
            @SuppressWarnings("unchecked")
            Map<String, Object> target = (Map<String, Object>) route.get("target");
            String input = "table-" + (index + 1);
            Map<String, Object> read = new LinkedHashMap<>(source);
            read.put("id", input);
            Object chunk = read.remove("chunkKeyColumn");
            read.remove("xa");
            if (chunk != null) read.put("snapshot", Map.of("chunkKeyColumn", chunk));
            if (route.containsKey("incremental")) read.put("incremental", route.get("incremental"));
            sourceTables.add(read);

            Map<String, Object> write = new LinkedHashMap<>();
            write.put("mode", route.get("writeMode"));
            if (target.containsKey("primaryKeys")) write.put("primaryKeys", target.get("primaryKeys"));
            if ("RETRACT".equals(route.get("writeMode"))) {
                write.put("transaction", Map.of("mode", "CHECKPOINT_XA"));
            }
            Map<String, Object> output = new LinkedHashMap<>(target);
            output.remove("primaryKeys");
            output.put("input", input);
            output.put("write", write);
            sinkTables.add(output);
        }
        Map<String, Object> policy = Map.of(
                "initial", steps.contains("SCHEMA") ? "CREATE_OR_ALTER" : "VERIFY",
                "evolution", Map.of("enabled", false, "allowDropColumns", false));
        return Map.of(
                "solutionType", solution,
                "steps", steps.stream().filter(step -> !"SCHEMA".equals(step)).toList(),
                "source", Map.of("datasourceId", sourceId, "tables", sourceTables),
                "transform", List.of(),
                "sink", Map.of("datasourceId", targetId, "schemaPolicy", policy, "tables", sinkTables),
                "env", Map.of());
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
                                parameters))
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
        Map<String, Object> parameters = Map.of(
                "solutionType", "SINGLE_TABLE_REALTIME",
                "steps", List.of("FULL", "INCREMENTAL"),
                "source", Map.of("datasourceId", sourceId, "tables", List.of(Map.of(
                        "id", "keyless",
                        "table", "keyless_source",
                        "keyless", true,
                        "snapshot", Map.of("chunkKeyColumn", "bucket")))),
                "transform", List.of(),
                "sink", Map.of(
                        "datasourceId", sourceId,
                        "schemaPolicy", Map.of("initial", "VERIFY",
                                "evolution", Map.of("enabled", false, "allowDropColumns", false)),
                        "tables", List.of(Map.of(
                                "input", "keyless",
                                "table", "keyless_target",
                                "write", Map.of(
                                        "mode", "RETRACT",
                                        "transaction", Map.of("mode", "CHECKPOINT_XA"))))),
                "env", Map.of());
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
                        "SELECT COUNT(*) FROM yak_ops_task_execution_claim WHERE instance_id=?")) {
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
