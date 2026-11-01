package io.yak.ops.plugin.task.datasync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskPluginException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts the Task JSON transport into a Flink CDC Pipeline YAML definition.
 *
 * <p>Only the Task Plugin knows the product's JSON contract. The engine accepts YAML and
 * uses the upstream Flink CDC parser; it never depends on this JSON representation.
 */
public final class PipelineYamlConverter {
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private PipelineYamlConverter() {}

    public static String convert(ReadonlyConfig parameters) {
        Map<String, Object> root = parameters.getSourceMap();
        checkKeys(root, "pipeline", "source", "sink", "route", "transform", "pipeline", "index");
        Map<String, Object> inputSource = object(root.get("source"), "source");
        Map<String, Object> inputSink = object(root.get("sink"), "sink");
        Map<String, Object> inputPipeline = object(root.get("pipeline"), "pipeline");
        checkKeys(inputSource, "source", "type", "datasourceId", "syncMode", "startup", "tables", "selection");
        checkKeys(inputSink, "sink", "type", "datasourceId", "schemaPolicy", "tables", "mapping", "typeMappings");
        checkKeys(
                inputPipeline,
                "pipeline",
                "execution.runtime-mode",
                "pipeline.table-scope",
                "name",
                "parallelism",
                "local-time-zone",
                "schema.change.behavior",
                "schema.operator.uid",
                "route-mode",
                "transform.decimal.precision.mode");

        String sourceType = identifier(inputSource);
        String sinkType = identifier(inputSink);
        List<Object> sourceTables = array(inputSource.get("tables"), "source.tables");
        List<Object> sinkTables = array(inputSink.get("tables"), "sink.tables");
        List<Object> routes = array(root.get("route"), "route");
        if (sourceTables.isEmpty()
                || sourceTables.size() != sinkTables.size()
                || routes.size() != sourceTables.size()) {
            throw new TaskPluginException("DATA_SYNC requires one route per source and target table");
        }

        Map<String, String> source = new LinkedHashMap<>();
        source.put("type", sourceType);
        source.put("datasource-id", required(inputSource, "datasourceId"));
        List<String> selectedTables = new ArrayList<>();
        boolean sourceDatabase = databaseNamespace(sourceTables, "source.table");
        for (Object item : sourceTables) {
            Map<String, Object> table = object(item, "source.table");
            checkKeys(
                    table,
                    "source.table",
                    "database",
                    "schema",
                    "table",
                    "snapshot",
                    "incremental",
                    "read",
                    "options",
                    "dml");
            String path = path(table);
            selectedTables.add(path);
            String prefix = "yak.table." + path + ".";
            if (table.containsKey("read")) {
                Map<String, Object> read = object(table.get("read"), "read");
                checkKeys(read, "read", "where", "splitKey");
                copyText(read, "where", source, prefix + "read.where");
                copyText(read, "splitKey", source, prefix + "read.split-key");
            }
            if (table.containsKey("options")) {
                Map<String, Object> options = object(table.get("options"), "source.options");
                checkKeys(options, "source.options", "fetchSize", "queryTimeoutSeconds");
                copyInteger(options, "fetchSize", source, prefix + "read.fetch-size", 10000);
                copyInteger(options, "queryTimeoutSeconds", source, prefix + "read.query-timeout-seconds", 300);
            }
            if (table.containsKey("snapshot")) {
                Map<String, Object> snapshot = object(table.get("snapshot"), "snapshot");
                snapshot(snapshot, source, prefix, false);
            }
            if (table.containsKey("dml")) {
                dml(object(table.get("dml"), "dml"), source, prefix);
            }
            if (table.containsKey("incremental")) {
                Map<String, Object> incremental = object(table.get("incremental"), "incremental");
                checkKeys(
                        incremental,
                        "incremental",
                        "mode",
                        "column",
                        "initialValue",
                        "delaySeconds",
                        "lookbackSeconds");
                source.put(prefix + "incremental.mode", required(incremental, "mode"));
                source.put(prefix + "incremental.column", required(incremental, "column"));
                copy(incremental, "initialValue", source, prefix + "incremental.initial-value");
                copy(incremental, "delaySeconds", source, prefix + "incremental.delay-seconds");
                copy(incremental, "lookbackSeconds", source, prefix + "incremental.lookback-seconds");
            }
        }
        source.put("tables", String.join(",", selectedTables));
        source.put("yak.namespace-kind", sourceDatabase ? "DATABASE" : "SCHEMA");
        if (inputSource.containsKey("selection") != inputSink.containsKey("mapping")) {
            throw new TaskPluginException("Dynamic table selection requires a target mapping template");
        }
        if (inputSource.containsKey("selection")) {
            Map<String, Object> selection = object(inputSource.get("selection"), "selection");
            checkKeys(selection, "selection", "mode", "databasePattern", "tablePattern", "snapshot", "dml");
            if (!"REGEX".equals(required(selection, "mode"))) {
                throw new TaskPluginException("Only REGEX selection is explicit; fixed tables omit selection");
            }
            source.put("yak.selection.mode", "REGEX");
            source.put("yak.selection.database-pattern", required(selection, "databasePattern"));
            source.put("yak.selection.table-pattern", required(selection, "tablePattern"));
            if (selection.containsKey("snapshot")) {
                snapshot(object(selection.get("snapshot"), "selection.snapshot"), source, "yak.selection.", true);
            }
            if (selection.containsKey("dml")) {
                dml(object(selection.get("dml"), "selection.dml"), source, "yak.selection.");
            }
        }

        if (inputSource.containsKey("startup")) {
            if (inputSource.containsKey("syncMode")) {
                throw new TaskPluginException("A source cannot combine CDC startup and JDBC syncMode");
            }
            Map<String, Object> startup = object(inputSource.get("startup"), "startup");
            checkKeys(
                    startup, "startup", "mode", "file", "pos", "gtidSet", "timestampMillis", "skipEvents", "skipRows");
            String mode = required(startup, "mode");
            String mapped =
                    switch (mode) {
                        case "INITIAL" -> "initial";
                        case "SNAPSHOT" -> "snapshot";
                        case "EARLIEST_OFFSET" -> "earliest-offset";
                        case "LATEST_OFFSET" -> "latest-offset";
                        case "SPECIFIC_OFFSETS" -> "specific-offset";
                        case "TIMESTAMP" -> "timestamp";
                        default -> throw new TaskPluginException("Unsupported CDC startup mode: " + mode);
                    };
            source.put("scan.startup.mode", mapped);
            copy(startup, "file", source, "scan.startup.specific-offset.file");
            copy(startup, "pos", source, "scan.startup.specific-offset.pos");
            copy(startup, "gtidSet", source, "scan.startup.specific-offset.gtid-set");
            copy(startup, "timestampMillis", source, "scan.startup.timestamp-millis");
            copy(startup, "skipEvents", source, "scan.startup.specific-offset.skip-events");
            copy(startup, "skipRows", source, "scan.startup.specific-offset.skip-rows");
        } else if (inputSource.containsKey("syncMode")) {
            source.put("yak.sync.mode", required(inputSource, "syncMode"));
        }

        Map<String, String> sink = new LinkedHashMap<>();
        sink.put("type", sinkType);
        sink.put("datasource-id", required(inputSink, "datasourceId"));
        boolean sinkDatabase = databaseNamespace(sinkTables, "sink.table");
        List<String> selectedSinkTables = new ArrayList<>();
        for (Object item : sinkTables) {
            Map<String, Object> table = object(item, "sink.table");
            checkKeys(table, "sink.table", "database", "schema", "table", "write");
            String targetPath = path(table);
            selectedSinkTables.add(targetPath);
            String prefix = "yak.table." + targetPath + ".";
            Map<String, Object> write = object(table.get("write"), "write");
            checkKeys(write, "write", "mode", "primaryKeys", "transaction", "preSql", "postSql", "batchSize");
            sink.put(prefix + "write.mode", required(write, "mode"));
            copyInteger(write, "batchSize", sink, prefix + "write.batch-size", 10000);
            copySql(write, "preSql", sink, prefix + "write.pre-sql");
            copySql(write, "postSql", sink, prefix + "write.post-sql");
            if (write.containsKey("primaryKeys")) {
                List<Object> keys = array(write.get("primaryKeys"), "write.primaryKeys");
                List<String> values = new ArrayList<>();
                for (Object key : keys) {
                    if (!(key instanceof String value) || value.isBlank()) {
                        throw new TaskPluginException("Invalid target primary key");
                    }
                    values.add(value);
                }
                sink.put(prefix + "write.primary-keys", String.join(",", values));
            }
            if (write.containsKey("transaction")) {
                Map<String, Object> tx = object(write.get("transaction"), "transaction");
                checkKeys(tx, "transaction", "mode");
                sink.put(prefix + "write.transaction.mode", required(tx, "mode"));
            }
        }
        sink.put("yak.namespace-kind", sinkDatabase ? "DATABASE" : "SCHEMA");
        if (inputSink.containsKey("mapping")) {
            Map<String, Object> mapping = object(inputSink.get("mapping"), "mapping");
            checkKeys(mapping, "mapping", "database", "schema", "table", "write");
            copyText(mapping, "database", sink, "yak.mapping.database");
            copyText(mapping, "schema", sink, "yak.mapping.schema");
            sink.put("yak.mapping.table", required(mapping, "table"));
            Map<String, Object> write = object(mapping.get("write"), "mapping.write");
            checkKeys(write, "mapping.write", "mode", "primaryKeys", "transaction", "batchSize");
            sink.put("yak.mapping.write.mode", required(write, "mode"));
            copyInteger(write, "batchSize", sink, "yak.mapping.write.batch-size", 10000);
            if (write.containsKey("primaryKeys")) {
                List<String> keys = strings(write.get("primaryKeys"), "mapping.write.primaryKeys", 64);
                sink.put("yak.mapping.write.primary-keys", String.join(",", keys));
            }
            if (write.containsKey("transaction")) {
                Map<String, Object> tx = object(write.get("transaction"), "mapping.write.transaction");
                checkKeys(tx, "mapping.write.transaction", "mode");
                sink.put("yak.mapping.write.transaction.mode", required(tx, "mode"));
            }
        }
        if (inputSink.containsKey("typeMappings")) {
            List<Object> rules = array(inputSink.get("typeMappings"), "typeMappings");
            if (rules.size() > 32) throw new TaskPluginException("Too many type mapping rules");
            for (Object item : rules) {
                Map<String, Object> rule = object(item, "typeMapping");
                checkKeys(rule, "typeMapping", "sourceType", "targetType");
                required(rule, "sourceType");
                required(rule, "targetType");
            }
            sink.put("yak.type-mappings", json(rules));
        }
        Map<String, Object> policy = object(inputSink.get("schemaPolicy"), "schemaPolicy");
        checkKeys(policy, "schemaPolicy", "initial", "evolution");
        Map<String, Object> evolution = object(policy.get("evolution"), "evolution");
        checkKeys(evolution, "evolution", "enabled", "allowDropColumns");
        sink.put("yak.schema.initial", required(policy, "initial"));
        sink.put("yak.schema.evolution.enabled", bool(evolution, "enabled"));
        sink.put("yak.schema.evolution.allow-drop-columns", bool(evolution, "allowDropColumns"));

        Set<String> declaredSources = new HashSet<>(selectedTables);
        Set<String> declaredSinks = new HashSet<>(selectedSinkTables);
        if (declaredSources.size() != sourceTables.size() || declaredSinks.size() != sinkTables.size()) {
            throw new TaskPluginException("Duplicate source or sink table in DATA_SYNC JSON");
        }
        Set<String> routedSources = new HashSet<>();
        Set<String> routedSinks = new HashSet<>();
        List<Map<String, String>> yamlRoutes = new ArrayList<>();
        for (Object item : routes) {
            Map<String, Object> route = object(item, "route");
            checkKeys(route, "route", "source-table", "sink-table");
            String fromTable = required(route, "source-table");
            String toTable = required(route, "sink-table");
            if (!declaredSources.contains(fromTable)
                    || !declaredSinks.contains(toTable)
                    || !routedSources.add(fromTable)
                    || !routedSinks.add(toTable)) {
                throw new TaskPluginException("DATA_SYNC route must bind declared tables exactly once");
            }
            yamlRoutes.add(Map.of("source-table", fromTable, "sink-table", toTable));
        }
        Map<String, String> pipeline = new LinkedHashMap<>();
        for (String option : inputPipeline.keySet()) {
            if (!"pipeline.table-scope".equals(option)) {
                copy(inputPipeline, option, pipeline, option);
            }
        }
        String runtime = required(inputPipeline, "execution.runtime-mode");
        pipeline.put("execution.runtime-mode", runtime);
        String scope = text(inputPipeline, "pipeline.table-scope");
        pipeline.put("yak.table-scope", scope == null ? (sourceTables.size() == 1 ? "SINGLE" : "DATABASE") : scope);

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("source", source);
        definition.put("sink", sink);
        definition.put("route", yamlRoutes);
        if (root.containsKey("transform")) {
            definition.put("transform", array(root.get("transform"), "transform"));
        }
        definition.put("pipeline", pipeline);
        try {
            return YAML_MAPPER.writeValueAsString(definition);
        } catch (JsonProcessingException failure) {
            throw new TaskPluginException("Failed to convert DATA_SYNC JSON into Pipeline YAML", failure);
        }
    }

    private static String identifier(Map<String, Object> endpoint) {
        String type = required(endpoint, "type");
        if (!type.matches("[a-z][a-z0-9-]*")) {
            throw new TaskPluginException("DATA_SYNC requires a Connector factory identifier");
        }
        return type;
    }

    private static void snapshot(
            Map<String, Object> options, Map<String, String> target, String prefix, boolean defaults) {
        checkKeys(options, "snapshot", "chunkKeyColumn", "skip", "where");
        copyText(
                options,
                "chunkKeyColumn",
                target,
                prefix + (defaults ? "snapshot.chunk-key-column" : "chunk-key-column"));
        copyText(options, "where", target, prefix + "snapshot.where");
        if (options.containsKey("skip")) target.put(prefix + "snapshot.skip", bool(options, "skip"));
    }

    private static void dml(Map<String, Object> options, Map<String, String> target, String prefix) {
        checkKeys(options, "dml", "events");
        List<String> events = strings(options.get("events"), "dml.events", 3);
        Set<String> allowed = Set.of("INSERT", "UPDATE", "DELETE");
        if (events.isEmpty() || new HashSet<>(events).size() != events.size() || !allowed.containsAll(events)) {
            throw new TaskPluginException("DML events must be a nonempty unique subset of INSERT, UPDATE and DELETE");
        }
        for (String event : allowed) {
            target.put(
                    prefix + "dml." + event.toLowerCase(java.util.Locale.ROOT), String.valueOf(events.contains(event)));
        }
    }

    private static void copyInteger(
            Map<String, Object> input, String name, Map<String, String> target, String key, int maximum) {
        if (!input.containsKey(name)) return;
        Object value = input.get(name);
        if (!(value instanceof Number)) throw new TaskPluginException("Expected an integer: " + name);
        try {
            int number = new java.math.BigDecimal(value.toString()).intValueExact();
            if (number < 1 || number > maximum) throw new ArithmeticException("Out of range");
            target.put(key, String.valueOf(number));
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new TaskPluginException("Invalid integer range: " + name, failure);
        }
    }

    private static void copySql(Map<String, Object> input, String name, Map<String, String> target, String key) {
        if (input.containsKey(name)) target.put(key, json(strings(input.get(name), name, 32)));
    }

    private static List<String> strings(Object input, String context, int maximum) {
        List<Object> items = array(input, context);
        if (items.size() > maximum) throw new TaskPluginException("Too many entries: " + context);
        List<String> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof String value) || value.isBlank() || value.length() > 16384) {
                throw new TaskPluginException("Invalid string entry: " + context);
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static String json(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new TaskPluginException("Failed to serialize connector options", failure);
        }
    }

    private static boolean hasDatabase(Map<String, Object> table) {
        String database = text(table, "database");
        return database != null && !database.isBlank();
    }

    private static boolean databaseNamespace(List<Object> tables, String context) {
        boolean database = hasDatabase(object(tables.getFirst(), context));
        for (Object table : tables) {
            if (hasDatabase(object(table, context)) != database) {
                throw new TaskPluginException("DATA_SYNC endpoint tables require a consistent namespace shape");
            }
        }
        return database;
    }

    private static String path(Map<String, Object> table) {
        List<String> segments = new ArrayList<>();
        for (String name : List.of("database", "schema", "table")) {
            String part = text(table, name);
            if (part != null && (!part.isBlank() || "table".equals(name))) {
                if (part.isBlank() || part.indexOf('.') >= 0) {
                    throw new TaskPluginException("Invalid DATA_SYNC table path segment");
                }
                segments.add(part);
            }
        }
        if (text(table, "table") == null) {
            throw new TaskPluginException("DATA_SYNC table name is required");
        }
        return String.join(".", segments);
    }

    private static void copy(Map<String, Object> source, String from, Map<String, String> target, String to) {
        if (source.containsKey(from)) {
            Object value = source.get(from);
            if (!(value instanceof String) && !(value instanceof Number)) {
                throw new TaskPluginException("Invalid DATA_SYNC option: " + from);
            }
            target.put(to, String.valueOf(value));
        }
    }

    private static void copyText(Map<String, Object> source, String from, Map<String, String> target, String to) {
        if (!source.containsKey(from)) return;
        if (!(source.get(from) instanceof String value) || value.length() > 16384) {
            throw new TaskPluginException("Invalid string DATA_SYNC option: " + from);
        }
        target.put(to, value);
    }

    private static String bool(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Boolean)) {
            throw new TaskPluginException("Required boolean DATA_SYNC option: " + field);
        }
        return value.toString();
    }

    private static String text(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String result)) {
            throw new TaskPluginException("Expected string DATA_SYNC option: " + field);
        }
        return result;
    }

    private static String required(Map<String, Object> values, String field) {
        String value = text(values, field);
        if (value == null || value.isBlank()) {
            throw new TaskPluginException("Required DATA_SYNC option: " + field);
        }
        return value;
    }

    private static void checkKeys(Map<String, Object> values, String context, String... allowed) {
        Set<String> keys = Set.of(allowed);
        for (String key : values.keySet()) {
            if (!keys.contains(key)) {
                throw new TaskPluginException("Unsupported " + context + " option: " + key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String context) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new TaskPluginException("Invalid DATA_SYNC object: " + context);
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new TaskPluginException("Invalid DATA_SYNC key in " + context);
            }
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String context) {
        if (!(value instanceof List<?> list)) {
            throw new TaskPluginException("Invalid DATA_SYNC array: " + context);
        }
        return (List<Object>) list;
    }
}
