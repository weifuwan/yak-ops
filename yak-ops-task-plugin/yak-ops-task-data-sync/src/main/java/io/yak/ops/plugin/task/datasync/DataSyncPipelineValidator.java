package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;

import org.apache.flink.cdc.cli.parser.YamlPipelineDefinitionParser;
import org.apache.flink.cdc.common.configuration.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validates the currently enabled Yak Ops execution profile. Flink CDC owns
 * the YAML grammar, while these rules describe which connectors and settings
 * can safely be executed by the installed runtime.
 */
public final class DataSyncPipelineValidator {

    private static final Set<String> TOP_LEVEL = keys("source", "sink", "route", "transform", "pipeline");
    private static final Set<String> SOURCE = keys(
            "type", "datasource-id", "tables", "scan.startup.mode", "server-time-zone");
    private static final Set<String> SINK = keys(
            "type", "datasource-id", "exclude.schema.changes");
    private static final Set<String> PIPELINE = keys(
            "name", "parallelism", "execution.runtime-mode", "local-time-zone",
            "schema.change.behavior", "schema.operator.uid", "route-mode",
            "transform.decimal.precision.mode");
    private static final Set<String> ROUTE = keys(
            "source-table", "sink-table", "description");

    public void validate(JsonNode definition) {
        object(definition, "definition", TOP_LEVEL);
        JsonNode source = definition.path("source");
        JsonNode sink = definition.path("sink");
        JsonNode pipeline = definition.path("pipeline");
        object(source, "source", SOURCE);
        object(sink, "sink", SINK);
        object(pipeline, "pipeline", PIPELINE);

        DataSyncParameters.datasourceId(source);
        DataSyncParameters.datasourceId(sink);
        require(source, "type", "mysql", "source.type");
        require(sink, "type", "jdbc", "sink.type");
        require(source, "scan.startup.mode", "initial", "source.scan.startup.mode");
        require(pipeline, "execution.runtime-mode", "STREAMING", "pipeline.execution.runtime-mode");

        String tables = text(source, "tables");
        if (!tables.startsWith("\\Q") || !tables.endsWith("\\E") || tables.length() <= 4) {
            throw new IllegalArgumentException("source.tables: single literal table required (\\Q...\\E)");
        }
        String unquoted = tables.substring(2, tables.length() - 2);
        if (!unquoted.matches("[A-Za-z0-9_$-]+\\.[A-Za-z0-9_$-]+")) {
            throw new IllegalArgumentException("source.tables: expected database.table");
        }

        JsonNode routes = definition.path("route");
        if (!routes.isArray() || routes.size() != 1) {
            throw new IllegalArgumentException("route: exactly one table mapping is supported");
        }
        JsonNode route = routes.get(0);
        object(route, "route[0]", ROUTE);
        if (!tables.equals(text(route, "source-table"))) {
            throw new IllegalArgumentException("route[0].source-table must equal source.tables");
        }
        String target = text(route, "sink-table");
        if (!target.matches("[A-Za-z0-9_$-]+\\.[A-Za-z0-9_$-]+")) {
            throw new IllegalArgumentException("route[0].sink-table: expected database.table");
        }

        JsonNode transforms = definition.path("transform");
        if (!transforms.isMissingNode() && (!transforms.isArray() || transforms.size() > 0)) {
            throw new IllegalArgumentException("transform: not yet supported by this runtime");
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
        String evolution = pipeline.path("schema.change.behavior").asText("exception");
        if (!"evolve".equalsIgnoreCase(evolution) && !"exception".equalsIgnoreCase(evolution)) {
            throw new IllegalArgumentException("pipeline.schema.change.behavior: unsupported setting");
        }
        JsonNode excludes = sink.path("exclude.schema.changes");
        if (!excludes.isArray() || excludes.size() != 1
                || !"drop.column".equals(excludes.get(0).asText())) {
            throw new IllegalArgumentException("sink.exclude.schema.changes must exclude drop.column");
        }
        try {
            // Validate the same YAML grammar and Flink CDC global options used at runtime.
            new YamlPipelineDefinitionParser().parse(JSONUtils.toJsonString(definition), new Configuration());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Flink CDC Pipeline YAML", e);
        }
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

    private static void object(JsonNode value, String label, Set<String> keys) {
        if (!value.isObject()) {
            throw new IllegalArgumentException(label + ": object required");
        }
        Iterator<String> fields = value.fieldNames();
        while (fields.hasNext()) {
            String key = fields.next();
            if (!keys.contains(key)) {
                throw new IllegalArgumentException(label + "." + key + ": unsupported field");
            }
        }
    }

    private static Set<String> keys(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
