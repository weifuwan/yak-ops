package io.yak.ops.plugin.task.datasync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskPluginException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task-boundary adapter: public V6 JSON -> Flink CDC 3.7 Pipeline YAML.
 *
 * <p>Only the Task Plugin knows the product's JSON contract. The engine accepts YAML and
 * uses the upstream Flink CDC parser; it never depends on this JSON representation.
 */
public final class PipelineYamlConverter {
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    private PipelineYamlConverter() {}

    public static String convert(ReadonlyConfig parameters) {
        Map<String, Object> root = parameters.getSourceMap();
        checkKeys(root, "pipeline", "source", "sink", "route", "transform", "pipeline", "index");
        Map<String, Object> inputSource = object(root.get("source"), "source");
        Map<String, Object> inputSink = object(root.get("sink"), "sink");
        Map<String, Object> inputPipeline = object(root.get("pipeline"), "pipeline");
        checkKeys(inputSource, "source", "type", "datasourceId", "syncMode", "startup", "tables");
        checkKeys(inputSink, "sink", "type", "datasourceId", "schemaPolicy", "tables");
        checkKeys(inputPipeline, "pipeline", "execution.runtime-mode", "pipeline.table-scope");

        String sourceType = required(inputSource, "type");
        if (!"MYSQL_CDC".equals(sourceType) && !"JDBC".equals(sourceType)) {
            throw new TaskPluginException("Unsupported DATA_SYNC source type");
        }
        if (!"JDBC".equals(required(inputSink, "type"))) {
            throw new TaskPluginException("Unsupported DATA_SYNC sink type");
        }
        boolean realtime = "MYSQL_CDC".equals(sourceType);
        List<Object> sourceTables = array(inputSource.get("tables"), "source.tables");
        List<Object> sinkTables = array(inputSink.get("tables"), "sink.tables");
        List<Object> routes = array(root.get("route"), "route");
        if (sourceTables.isEmpty() || sourceTables.size() != sinkTables.size()
                || routes.size() != sourceTables.size()) {
            throw new TaskPluginException("DATA_SYNC requires one route per source and target table");
        }
        if (root.containsKey("transform") && !array(root.get("transform"), "transform").isEmpty()) {
            throw new TaskPluginException("YakFlow does not support transforms");
        }

        Map<String, String> source = new LinkedHashMap<>();
        source.put("type", realtime ? "mysql" : "jdbc");
        source.put("datasource-id", required(inputSource, "datasourceId"));
        List<String> selectedTables = new ArrayList<>();
        boolean sourceDatabase = false;
        for (Object item : sourceTables) {
            Map<String, Object> table = object(item, "source.table");
            checkKeys(table, "source.table", "database", "schema", "table", "snapshot", "incremental");
            String path = path(table);
            selectedTables.add(path);
            sourceDatabase |= text(table, "database") != null;
            String prefix = "yak.table." + path + ".";
            if (table.containsKey("snapshot")) {
                Map<String, Object> snapshot = object(table.get("snapshot"), "snapshot");
                checkKeys(snapshot, "snapshot", "chunkKeyColumn");
                source.put(prefix + "chunk-key-column", required(snapshot, "chunkKeyColumn"));
            }
            if (table.containsKey("incremental")) {
                Map<String, Object> incremental = object(table.get("incremental"), "incremental");
                checkKeys(incremental, "incremental",
                        "mode", "column", "initialValue", "delaySeconds", "lookbackSeconds");
                source.put(prefix + "incremental.mode", required(incremental, "mode"));
                source.put(prefix + "incremental.column", required(incremental, "column"));
                copy(incremental, "initialValue", source, prefix + "incremental.initial-value");
                copy(incremental, "delaySeconds", source, prefix + "incremental.delay-seconds");
                copy(incremental, "lookbackSeconds", source, prefix + "incremental.lookback-seconds");
            }
        }
        source.put("tables", String.join(",", selectedTables));
        source.put("yak.namespace-kind", sourceDatabase ? "DATABASE" : "SCHEMA");

        if (realtime) {
            if (inputSource.containsKey("syncMode")) {
                throw new TaskPluginException("MySQL CDC does not support JDBC syncMode");
            }
            Map<String, Object> startup = inputSource.containsKey("startup")
                    ? object(inputSource.get("startup"), "startup")
                    : Map.of("mode", "INITIAL");
            checkKeys(startup, "startup",
                    "mode", "file", "pos", "gtidSet", "timestampMillis", "skipEvents", "skipRows");
            String mode = required(startup, "mode");
            String mapped = switch (mode) {
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
        } else {
            if (inputSource.containsKey("startup")) {
                throw new TaskPluginException("JDBC source does not support CDC startup");
            }
            source.put("yak.sync.mode", inputSource.containsKey("syncMode")
                    ? required(inputSource, "syncMode") : "FULL");
        }

        Map<String, String> sink = new LinkedHashMap<>();
        sink.put("type", "jdbc");
        sink.put("datasource-id", required(inputSink, "datasourceId"));
        boolean sinkDatabase = false;
        for (Object item : sinkTables) {
            Map<String, Object> table = object(item, "sink.table");
            checkKeys(table, "sink.table", "database", "schema", "table", "write");
            String prefix = "yak.table." + path(table) + ".";
            sinkDatabase |= text(table, "database") != null;
            Map<String, Object> write = object(table.get("write"), "write");
            checkKeys(write, "write", "mode", "primaryKeys", "transaction");
            sink.put(prefix + "write.mode", required(write, "mode"));
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
        Map<String, Object> policy = object(inputSink.get("schemaPolicy"), "schemaPolicy");
        checkKeys(policy, "schemaPolicy", "initial", "evolution");
        Map<String, Object> evolution = object(policy.get("evolution"), "evolution");
        checkKeys(evolution, "evolution", "enabled", "allowDropColumns");
        sink.put("yak.schema.initial", required(policy, "initial"));
        sink.put("yak.schema.evolution.enabled", bool(evolution, "enabled"));
        sink.put("yak.schema.evolution.allow-drop-columns", bool(evolution, "allowDropColumns"));

        List<Map<String, String>> yamlRoutes = new ArrayList<>();
        for (Object item : routes) {
            Map<String, Object> route = object(item, "route");
            checkKeys(route, "route", "source-table", "sink-table");
            yamlRoutes.add(Map.of(
                    "source-table", required(route, "source-table"),
                    "sink-table", required(route, "sink-table")));
        }
        Map<String, String> pipeline = new LinkedHashMap<>();
        String runtime = text(inputPipeline, "execution.runtime-mode");
        if (runtime == null) {
            String startup = source.getOrDefault("scan.startup.mode", "initial");
            boolean streaming = realtime && !"snapshot".equals(startup);
            runtime = streaming ? "STREAMING" : "BATCH";
        }
        pipeline.put("execution.runtime-mode", runtime);
        String scope = text(inputPipeline, "pipeline.table-scope");
        pipeline.put("yak.table-scope", scope == null
                ? (sourceTables.size() == 1 ? "SINGLE" : "DATABASE") : scope);

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("source", source);
        definition.put("sink", sink);
        definition.put("route", yamlRoutes);
        definition.put("pipeline", pipeline);
        try {
            return YAML_MAPPER.writeValueAsString(definition);
        } catch (JsonProcessingException failure) {
            throw new TaskPluginException("Failed to convert DATA_SYNC JSON into Pipeline YAML", failure);
        }
    }

    private static String path(Map<String, Object> table) {
        List<String> segments = new ArrayList<>();
        for (String name : List.of("database", "schema", "table")) {
            String part = text(table, name);
            if (part != null) {
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

    private static void copy(
            Map<String, Object> source, String from, Map<String, String> target, String to) {
        if (source.containsKey(from)) {
            Object value = source.get(from);
            if (!(value instanceof String) && !(value instanceof Number)) {
                throw new TaskPluginException("Invalid DATA_SYNC option: " + from);
            }
            target.put(to, String.valueOf(value));
        }
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
