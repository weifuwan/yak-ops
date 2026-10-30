package io.yak.ops.core.parser;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.core.definition.RouteDef;
import io.yak.ops.core.definition.SinkDef;
import io.yak.ops.core.definition.SourceDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON implementation of Flink CDC-style declarative pipeline parsing.
 * V5 is the only supported pipeline schema.
 */
public final class JsonPipelineDefinitionParser implements PipelineDefinitionParser {
    private static final Set<String> ROOT_FIELDS = Set.of("source", "sink", "route", "transform", "pipeline");
    private static final Set<String> SOURCE_FIELDS = Set.of("type", "datasourceId", "startup", "syncMode", "tables");
    private static final Set<String> SINK_FIELDS = Set.of("type", "datasourceId", "schemaPolicy", "tables");

    @Override
    public PipelineDef parse(String content) {
        ReadonlyConfig raw = ConfigParser.parseJson(content);
        Map<String, Object> values = raw.getSourceMap();
        checkKeys(values, ROOT_FIELDS, "pipeline definition");
        Map<String, Object> source = object(values.get("source"), "source");
        Map<String, Object> sink = object(values.get("sink"), "sink");
        checkKeys(source, SOURCE_FIELDS, "source");
        checkKeys(sink, SINK_FIELDS, "sink");
        String sourceType = requiredString(source, "type", "source");
        String sinkType = requiredString(sink, "type", "sink");
        requiredString(source, "datasourceId", "source");
        requiredString(sink, "datasourceId", "sink");
        List<Object> sourceTables = array(source.get("tables"), "source.tables");
        List<Object> sinkTables = array(sink.get("tables"), "sink.tables");
        if (sourceTables.isEmpty() || sourceTables.size() > 100 || sourceTables.size() != sinkTables.size()) {
            throw new IllegalArgumentException("V5 requires 1–100 one-to-one source/sink tables");
        }
        for (Object table : sourceTables) validateTable(object(table, "source.tables"), false);
        for (Object table : sinkTables) validateTable(object(table, "sink.tables"), true);
        if (sink.get("schemaPolicy") == null) {
            throw new IllegalArgumentException("sink.schemaPolicy is required");
        }
        object(sink.get("schemaPolicy"), "sink.schemaPolicy");
        if (source.containsKey("startup")) object(source.get("startup"), "source.startup");
        if (source.containsKey("syncMode")) requiredString(source, "syncMode", "source");
        List<Object> transform =
                values.containsKey("transform") ? array(values.get("transform"), "transform") : List.of();
        if (!transform.isEmpty()) {
            throw new IllegalArgumentException("V5 transforms require a supported YakFlow operator");
        }
        List<Object> routes = values.containsKey("route") ? array(values.get("route"), "route") : List.of();
        if (routes.size() != sourceTables.size()) {
            throw new IllegalArgumentException("V5 requires an explicit route for every source table");
        }
        List<RouteDef> mapped = new ArrayList<>();
        Set<String> sources = new HashSet<>();
        Set<String> sinks = new HashSet<>();
        for (Object entry : routes) {
            Map<String, Object> route = object(entry, "route");
            checkKeys(route, Set.of("source-table", "sink-table"), "route");
            String from = requiredString(route, "source-table", "route");
            String to = requiredString(route, "sink-table", "route");
            if (!sources.add(from) || !sinks.add(to)) {
                throw new IllegalArgumentException("Duplicate V5 source or sink route");
            }
            mapped.add(new RouteDef(from, to));
        }
        Set<String> physicalSources = new HashSet<>();
        Set<String> physicalSinks = new HashSet<>();
        for (Object entry : sourceTables) physicalSources.add(tablePath(object(entry, "source.tables")));
        for (Object entry : sinkTables) physicalSinks.add(tablePath(object(entry, "sink.tables")));
        if (!sources.equals(physicalSources) || !sinks.equals(physicalSinks)) {
            throw new IllegalArgumentException("V5 route must reference each configured physical table once");
        }
        Map<String, Object> pipeline =
                values.containsKey("pipeline") ? object(values.get("pipeline"), "pipeline") : Map.of();
        checkKeys(pipeline, Set.of("execution.runtime-mode", "pipeline.table-scope"), "pipeline");
        if (pipeline.containsKey("pipeline.table-scope")) {
            String scope = requiredString(pipeline, "pipeline.table-scope", "pipeline");
            if (!"SINGLE".equals(scope) && !"DATABASE".equals(scope)) {
                throw new IllegalArgumentException("Unsupported V5 pipeline.table-scope");
            }
            if ("SINGLE".equals(scope) && sourceTables.size() != 1) {
                throw new IllegalArgumentException("SINGLE scope requires exactly one route");
            }
        }
        if (pipeline.containsKey("execution.runtime-mode")) {
            String mode = requiredString(pipeline, "execution.runtime-mode", "pipeline");
            if (!"BATCH".equals(mode) && !"STREAMING".equals(mode)) {
                throw new IllegalArgumentException("Unsupported V5 execution.runtime-mode");
            }
        }
        return new PipelineDef(
                raw,
                new SourceDef(sourceType, ReadonlyConfig.fromMap(source)),
                new SinkDef(sinkType, ReadonlyConfig.fromMap(sink)),
                mapped,
                List.of(),
                ReadonlyConfig.fromMap(pipeline));
    }

    private static void validateTable(Map<String, Object> table, boolean sink) {
        checkKeys(
                table,
                sink
                        ? Set.of("database", "schema", "table", "write")
                        : Set.of("database", "schema", "table", "snapshot", "incremental"),
                "table");
        requiredString(table, "table", "table");
        for (String optional : List.of("database", "schema")) {
            if (table.containsKey(optional)) requiredString(table, optional, "table");
        }
        if (sink) {
            if (!table.containsKey("write")) {
                throw new IllegalArgumentException("sink.tables.write is required");
            }
            object(table.get("write"), "sink.tables.write");
        } else {
            if (table.containsKey("snapshot")) object(table.get("snapshot"), "source.tables.snapshot");
            if (table.containsKey("incremental")) object(table.get("incremental"), "source.tables.incremental");
        }
    }

    private static String tablePath(Map<String, Object> table) {
        List<String> names = new ArrayList<>();
        for (String field : List.of("database", "schema", "table")) {
            if (table.containsKey(field)) {
                String value = requiredString(table, field, "table");
                if (value.indexOf('.') >= 0) {
                    throw new IllegalArgumentException("Dots in V5 physical table names are not yet supported");
                }
                names.add(value);
            }
        }
        return String.join(".", names);
    }

    private static void checkKeys(Map<String, Object> map, Set<String> allowed, String section) {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown " + section + " field: " + key);
            }
        }
    }

    private static String requiredString(Map<String, Object> values, String field, String section) {
        Object value = values.get(field);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(section + "." + field + " must be a nonblank string");
        }
        return string;
    }

    private static List<Object> array(Object value, String section) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(section + " must be an array");
        }
        return new ArrayList<>(list);
    }

    private static Map<String, Object> object(Object value, String section) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(section + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(section + " contains a non-string key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }
}
