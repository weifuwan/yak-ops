package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.core.definition.RouteDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes declarative V5 into the existing verified V4 execution contract.
 * There is exactly one physical JobPlanner/PipelineBuilder, not a V5 execution fork.
 * Retire this adapter once the planner consumes PipelineDef natively.
 */
public final class V5PipelineAdapter {

    private V5PipelineAdapter() {}

    public static ReadonlyConfig toV4(PipelineDef definition) {
        if (definition.getVersion() == 4) return definition.getOriginal();
        if (definition.getVersion() != 5) throw new IllegalArgumentException("Unsupported pipeline definition version");
        Map<String, Object> source = new LinkedHashMap<>(definition.getSource().getConfig().getSourceMap());
        Map<String, Object> sink = new LinkedHashMap<>(definition.getSink().getConfig().getSourceMap());
        String sourceType = definition.getSource().getType();
        String sinkType = definition.getSink().getType();
        if (!"JDBC".equals(sinkType) || (!"JDBC".equals(sourceType) && !"MYSQL_CDC".equals(sourceType))) {
            throw new IllegalArgumentException("V5 connector combination is not supported by YakFlow");
        }
        boolean realtime = "MYSQL_CDC".equals(sourceType);
        List<Object> sources = array(source.remove("tables"), "source.tables");
        List<Object> sinks = array(sink.remove("tables"), "sink.tables");
        if (sources.size() != sinks.size() || sources.size() != definition.getRoute().size()) {
            throw new IllegalArgumentException("V5 route and table counts differ");
        }
        String solution = sources.size() == 1
                ? (realtime ? "SINGLE_TABLE_REALTIME" : "SINGLE_TABLE_OFFLINE")
                : (realtime ? "DATABASE_REALTIME" : "DATABASE_OFFLINE");

        String syncMode = string(source.remove("syncMode"), "source.syncMode", false);
        Map<String, Object> startup = source.containsKey("startup")
                ? object(source.get("startup"), "source.startup") : null;
        List<String> steps;
        if (realtime) {
            if (syncMode != null) {
                throw new IllegalArgumentException("MYSQL_CDC uses startup, not syncMode");
            }
            String mode = startup == null ? "INITIAL" : string(startup.get("mode"), "startup.mode", true);
            steps = switch (mode) {
                case "INITIAL" -> List.of("FULL", "INCREMENTAL");
                case "SNAPSHOT" -> List.of("FULL");
                case "EARLIEST_OFFSET", "LATEST_OFFSET", "SPECIFIC_OFFSETS", "TIMESTAMP" ->
                    List.of("INCREMENTAL");
                default -> throw new IllegalArgumentException("Unsupported MySQL CDC startup mode");
            };
        } else {
            if (startup != null) throw new IllegalArgumentException("JDBC does not accept CDC startup");
            steps = switch (syncMode == null ? "FULL" : syncMode) {
                case "FULL" -> List.of("FULL");
                case "INCREMENTAL" -> List.of("INCREMENTAL");
                case "FULL_AND_INCREMENTAL" -> List.of("FULL", "INCREMENTAL");
                default -> throw new IllegalArgumentException("Unsupported JDBC syncMode");
            };
        }
        String mode = string(definition.getConfig().getSourceMap().get("execution.runtime-mode"),
                "pipeline.execution.runtime-mode", false);
        String expected = realtime && !steps.equals(List.of("FULL")) ? "STREAMING" : "BATCH";
        if (mode != null && !expected.equals(mode)) {
            throw new IllegalArgumentException("Pipeline runtime mode conflicts with Source execution mode");
        }
        Map<String, Map<String, Object>> sourceByPath = index(sources, "source");
        Map<String, Map<String, Object>> sinkByPath = index(sinks, "sink");
        List<Object> mappedSources = new ArrayList<>();
        List<Object> mappedSinks = new ArrayList<>();
        Set<String> routed = new HashSet<>();
        for (RouteDef route : definition.getRoute()) {
            String sourcePath = route.getSourceTable();
            String sinkPath = route.getSinkTable();
            Map<String, Object> input = sourceByPath.remove(sourcePath);
            Map<String, Object> output = sinkByPath.remove(sinkPath);
            if (input == null || output == null || !routed.add(sourcePath)) {
                throw new IllegalArgumentException("V5 route refers to missing or duplicated physical table");
            }
            // The V4 ID is local to this definition and stable across retries.
            input.put("id", sourcePath);
            output.put("input", sourcePath);
            mappedSources.add(input);
            mappedSinks.add(output);
        }
        if (!sourceByPath.isEmpty() || !sinkByPath.isEmpty()) {
            throw new IllegalArgumentException("Unmapped V5 source or sink table");
        }
        source.put("tables", mappedSources);
        sink.put("tables", mappedSinks);
        Map<String, Object> v4 = new LinkedHashMap<>();
        v4.put("solutionType", solution);
        v4.put("steps", steps);
        v4.put("source", source);
        v4.put("sink", sink);
        v4.put("transform", List.of());
        v4.put("env", Map.of());
        return ReadonlyConfig.fromMap(v4);
    }

    private static Map<String, Map<String, Object>> index(List<Object> definitions, String section) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Object value : definitions) {
            Map<String, Object> table = object(value, section + ".tables");
            String path = path(table);
            if (indexed.putIfAbsent(path, table) != null) {
                throw new IllegalArgumentException("Duplicate " + section + " physical table: " + path);
            }
        }
        return indexed;
    }

    private static String path(Map<String, Object> table) {
        List<String> segments = new ArrayList<>();
        for (String key : List.of("database", "schema", "table")) {
            String value = string(table.get(key), key, "table".equals(key));
            if (value != null) {
                if (value.indexOf('.') >= 0) {
                    throw new IllegalArgumentException("V5 route identifiers do not support dots inside names");
                }
                segments.add(value);
            }
        }
        return String.join(".", segments);
    }

    private static String string(Object value, String field, boolean required) {
        if (value == null && !required) return null;
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " must be a nonblank string");
        }
        return text;
    }

    private static List<Object> array(Object value, String field) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(field + " must be an array");
        return new ArrayList<>(list);
    }

    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> input)) throw new IllegalArgumentException(field + " must be an object");
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException(field + " invalid key");
            mapped.put(key, entry.getValue());
        }
        return mapped;
    }
}
