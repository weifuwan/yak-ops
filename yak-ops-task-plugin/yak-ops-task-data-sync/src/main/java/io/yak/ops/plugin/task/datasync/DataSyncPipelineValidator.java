package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;

import org.apache.flink.cdc.cli.parser.YamlPipelineDefinitionParser;
import org.apache.flink.cdc.common.configuration.Configuration;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validates the currently enabled Yak Ops execution profile. Flink CDC owns
 * the YAML grammar, while these rules describe which connectors and settings
 * can safely be executed by the installed runtime.
 */
public final class DataSyncPipelineValidator {

    private static final Set<String> SCHEMA_CHANGE_BEHAVIORS =
            Set.of("ignore", "lenient", "try_evolve", "evolve", "exception");

    private static final Set<String> SCHEMA_CHANGE_EVENTS =
            Set.of(
                    "create.table",
                    "add.column",
                    "alter.column.type",
                    "rename.column",
                    "drop.column",
                    "truncate.table",
                    "drop.table",
                    "alter.table.comment");

    private static final Set<String> SUPPORTED_TRANSFORM_KEYS =
            Set.of("source-table", "projection", "filter", "primary-keys");

    public void validate(JsonNode definition) {
        object(definition, "definition");
        rejectCredentials(definition);
        JsonNode source = definition.path("source");
        JsonNode sink = definition.path("sink");
        JsonNode pipeline = definition.path("pipeline");
        object(source, "source");
        object(sink, "sink");
        object(pipeline, "pipeline");

        DataSyncParameters.datasourceId(source);
        DataSyncParameters.datasourceId(sink);
        require(source, "type", "mysql", "source.type");
        require(sink, "type", "jdbc", "sink.type");
        String startupMode = text(source, "scan.startup.mode").toLowerCase(Locale.ROOT);
        String runtimeMode = text(pipeline, "execution.runtime-mode").toUpperCase(Locale.ROOT);
        if (!Set.of("initial", "snapshot", "latest-offset").contains(startupMode)) {
            throw new IllegalArgumentException("source.scan.startup.mode: unsupported value");
        }
        if (("snapshot".equals(startupMode) && !"BATCH".equals(runtimeMode))
                || (!"snapshot".equals(startupMode) && !"STREAMING".equals(runtimeMode))) {
            throw new IllegalArgumentException(
                    "pipeline.execution.runtime-mode does not match source.scan.startup.mode");
        }

        String tables = text(source, "tables");
        List<String> sourceTables = Arrays.stream(tables.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
        boolean databaseScope = sourceTables.size() == 1 && isDatabasePattern(sourceTables.get(0));
        if (!databaseScope
                && (sourceTables.isEmpty()
                        || sourceTables.stream().anyMatch(value -> !isTablePath(value))
                        || new LinkedHashSet<>(sourceTables).size() != sourceTables.size())) {
            throw new IllegalArgumentException(
                    "source.tables: expected unique database.table values or one database-wide pattern");
        }

        JsonNode routes = definition.path("route");
        Set<String> sourceTableSet = new LinkedHashSet<>(sourceTables);
        if (databaseScope) {
            if (!routes.isArray() || routes.size() != 1) {
                throw new IllegalArgumentException("route: database sync requires one route");
            }
            JsonNode route = routes.get(0);
            object(route, "route[0]");
            if (!sourceTables.get(0).equals(text(route, "source-table"))
                    || !isDatabaseSinkPattern(text(route, "sink-table"))
                    || !"<>".equals(text(route, "replace-symbol"))) {
                throw new IllegalArgumentException(
                        "route[0]: database sync must preserve source table names");
            }
        } else {
            if (!routes.isArray() || routes.size() != sourceTables.size()) {
                throw new IllegalArgumentException(
                        "route: every source table must have exactly one target mapping");
            }
            Set<String> routedSources = new LinkedHashSet<>();
            Set<String> routedTargets = new LinkedHashSet<>();
            for (int index = 0; index < routes.size(); index++) {
                JsonNode route = routes.get(index);
                object(route, "route[" + index + "]");
                String routeSource = text(route, "source-table");
                String routeTarget = text(route, "sink-table");
                if (!isTablePath(routeSource)
                        || !sourceTableSet.contains(routeSource)
                        || !routedSources.add(routeSource)) {
                    throw new IllegalArgumentException(
                            "route[" + index + "].source-table must uniquely reference source.tables");
                }
                if (!isTablePath(routeTarget)) {
                    throw new IllegalArgumentException(
                            "route[" + index + "].sink-table: expected database.table");
                }
                if (!routedTargets.add(routeTarget)) {
                    throw new IllegalArgumentException(
                            "route[" + index + "].sink-table: duplicate target mapping");
                }
            }
            if (!routedSources.equals(sourceTableSet)) {
                throw new IllegalArgumentException(
                        "route: every source table must have exactly one target mapping");
            }
        }

        JsonNode transforms = definition.path("transform");
        if (databaseScope && !transforms.isMissingNode() && transforms.size() > 0) {
            throw new IllegalArgumentException("transform: database sync does not support table-level transforms");
        }
        if (!transforms.isMissingNode()) {
            if (!transforms.isArray()) {
                throw new IllegalArgumentException("transform: array required");
            }
            Set<String> transformedSources = new LinkedHashSet<>();
            for (int index = 0; index < transforms.size(); index++) {
                JsonNode transform = transforms.get(index);
                object(transform, "transform[" + index + "]");
                String transformSource = text(transform, "source-table");
                if (!sourceTableSet.contains(transformSource)
                        || !transformedSources.add(transformSource)) {
                    throw new IllegalArgumentException(
                            "transform[" + index + "].source-table must uniquely reference source.tables");
                }
                Iterator<Map.Entry<String, JsonNode>> fields = transform.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    if (!SUPPORTED_TRANSFORM_KEYS.contains(field.getKey())) {
                        throw new IllegalArgumentException(
                                "transform[" + index + "]." + field.getKey() + ": unsupported setting");
                    }
                    if (!"source-table".equals(field.getKey())
                            && (!field.getValue().isTextual()
                                    || field.getValue().asText().trim().isEmpty())) {
                        throw new IllegalArgumentException(
                                "transform[" + index + "]." + field.getKey()
                                        + ": non-empty string required");
                    }
                }
            }
        }

        JsonNode chunkKeyColumn =
                source.path("scan.incremental.snapshot.chunk.key-column");
        if (databaseScope && !chunkKeyColumn.isMissingNode()) {
            throw new IllegalArgumentException(
                    "source.scan.incremental.snapshot.chunk.key-column: unsupported for database sync");
        }
        if (!chunkKeyColumn.isMissingNode()) {
            if (!chunkKeyColumn.isTextual() || chunkKeyColumn.asText().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "source.scan.incremental.snapshot.chunk.key-column: non-empty string required");
            }
            Set<String> configuredTables = new LinkedHashSet<>();
            for (String item : chunkKeyColumn.asText().split(";")) {
                String entry = item.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                int separator = entry.lastIndexOf(':');
                String sourceTable = separator > 0 ? entry.substring(0, separator) : "";
                String column = separator > 0 ? entry.substring(separator + 1).trim() : "";
                if (!sourceTableSet.contains(sourceTable)
                        || column.isEmpty()
                        || !configuredTables.add(sourceTable)) {
                    throw new IllegalArgumentException(
                            "source.scan.incremental.snapshot.chunk.key-column: invalid table mapping");
                }
            }
            if (configuredTables.isEmpty()) {
                throw new IllegalArgumentException(
                        "source.scan.incremental.snapshot.chunk.key-column: invalid table mapping");
            }
        }

        JsonNode parallelism = pipeline.path("parallelism");
        if (!parallelism.isMissingNode()) {
            try {
                int count = Integer.parseInt(parallelism.asText());
                if (count < 1 || count > 128) {
                    throw new IllegalArgumentException("pipeline.parallelism must be between 1 and 128");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("pipeline.parallelism must be an integer", e);
            }
        }
        String evolution = pipeline.path("schema.change.behavior").asText("lenient")
                .toLowerCase(Locale.ROOT);
        if (!SCHEMA_CHANGE_BEHAVIORS.contains(evolution)) {
            throw new IllegalArgumentException("pipeline.schema.change.behavior: unsupported setting");
        }
        JsonNode excludes = sink.path("exclude.schema.changes");
        if (!excludes.isMissingNode()) {
            if (!excludes.isArray()) {
                throw new IllegalArgumentException("sink.exclude.schema.changes: array required");
            }
            for (JsonNode event : excludes) {
                if (!event.isTextual() || !SCHEMA_CHANGE_EVENTS.contains(event.asText())) {
                    throw new IllegalArgumentException(
                            "sink.exclude.schema.changes: unsupported schema change type");
                }
            }
        }
        try {
            // The Flink parser removes some schema-change fields while parsing, so validate a copy.
            JsonNode flinkDefinition = definition.deepCopy();
            new YamlPipelineDefinitionParser().parse(
                    JSONUtils.toJsonString(flinkDefinition), new Configuration());
        } catch (Exception e) {
            String reason = e.getMessage();
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid Flink CDC Pipeline YAML", e);
            }
            throw new IllegalArgumentException("Invalid Flink CDC Pipeline YAML: " + reason, e);
        }
    }

    private static boolean isTablePath(String value) {
        return value.matches("[A-Za-z0-9_$-]+\\.[A-Za-z0-9_$-]+");
    }

    private static boolean isDatabasePattern(String value) {
        int separator = value.indexOf('.');
        return separator > 0
                && value.substring(0, separator).matches("[A-Za-z0-9_$-]+")
                && "\\.*".equals(value.substring(separator + 1));
    }

    private static boolean isDatabaseSinkPattern(String value) {
        int separator = value.indexOf('.');
        return separator > 0
                && value.substring(0, separator).matches("[A-Za-z0-9_$-]+")
                && "<>".equals(value.substring(separator + 1));
    }

    private static String text(JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (!value.isTextual() || value.asText().trim().isEmpty()) {
            throw new IllegalArgumentException(key + ": non-empty string required");
        }
        return value.asText();
    }

    private static void require(JsonNode object, String key, String expected, String label) {
        if (!expected.equalsIgnoreCase(text(object, key))) {
            throw new IllegalArgumentException(label + ": unsupported value");
        }
    }

    private static void object(JsonNode value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + ": object required");
        }
    }

    private static void rejectCredentials(JsonNode value) {
        if (value.isArray()) {
            value.forEach(DataSyncPipelineValidator::rejectCredentials);
            return;
        }
        if (!value.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
            if ("password".equals(key) || "passwd".equals(key) || "token".equals(key)
                    || "secret".equals(key) || "secretkey".equals(key)
                    || "accesskey".equals(key) || "apikey".equals(key)
                    || "privatekey".equals(key) || "credentials".equals(key)) {
                throw new IllegalArgumentException(
                        "Pipeline must reference Yak Ops datasources, not inline credentials");
            }
            rejectCredentials(field.getValue());
        }
    }

}
