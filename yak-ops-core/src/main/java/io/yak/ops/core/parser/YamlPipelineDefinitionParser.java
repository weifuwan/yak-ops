package io.yak.ops.core.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.core.definition.RouteDef;
import io.yak.ops.core.definition.SinkDef;
import io.yak.ops.core.definition.SourceDef;
import io.yak.ops.core.definition.TransformDef;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parser for converting YAML pipeline definitions to PipelineDef.
 *
 * <p>Follows the parsing boundary and node structure of Apache Flink CDC 3.7's
 * YamlPipelineDefinitionParser. Only Yak Ops' datasource-id is platform-specific.
 * Connector options stay opaque until their factories validate them.
 */
public final class YamlPipelineDefinitionParser implements PipelineDefinitionParser {
    private static final String SOURCE_KEY = "source";
    private static final String SINK_KEY = "sink";
    private static final String ROUTE_KEY = "route";
    private static final String TRANSFORM_KEY = "transform";
    private static final String PIPELINE_KEY = "pipeline";
    private static final String TYPE_KEY = "type";
    private static final String NAME_KEY = "name";
    private static final String ROUTE_SOURCE_TABLE_KEY = "source-table";
    private static final String ROUTE_SINK_TABLE_KEY = "sink-table";
    private static final String ROUTE_REPLACE_SYMBOL = "replace-symbol";
    private static final String ROUTE_DESCRIPTION_KEY = "description";
    private static final String TRANSFORM_SOURCE_TABLE_KEY = "source-table";
    private static final String TRANSFORM_PROJECTION_KEY = "projection";
    private static final String TRANSFORM_FILTER_KEY = "filter";
    private static final ObjectMapper MAPPER =
            new ObjectMapper(new YAMLFactory()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    @Override
    public PipelineDef parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Pipeline YAML is required");
        }
        try {
            JsonNode root = MAPPER.readTree(content);
            validateNodeKeys("top-level", root, Set.of(SOURCE_KEY, SINK_KEY),
                    Set.of(ROUTE_KEY, TRANSFORM_KEY, PIPELINE_KEY));

            SourceDef sourceDef = toSourceDef(required(root, SOURCE_KEY, "pipeline"));
            SinkDef sinkDef = toSinkDef(required(root, SINK_KEY, "pipeline"));
            List<RouteDef> routes = new ArrayList<>();
            JsonNode routeNodes = root.get(ROUTE_KEY);
            if (routeNodes != null) {
                validateArray(ROUTE_KEY, routeNodes);
                for (JsonNode routeNode : routeNodes) {
                    routes.add(toRouteDef(routeNode));
                }
            }
            List<TransformDef> transforms = new ArrayList<>();
            JsonNode transformNodes = root.get(TRANSFORM_KEY);
            if (transformNodes != null) {
                validateArray(TRANSFORM_KEY, transformNodes);
                for (JsonNode transformNode : transformNodes) {
                    transforms.add(toTransformDef(transformNode));
                }
            }
            ReadonlyConfig pipelineConfig = toConfiguration(root.get(PIPELINE_KEY), PIPELINE_KEY);
            return new PipelineDef(
                    ReadonlyConfig.fromMap(Map.of("pipelineYaml", content)), sourceDef, sinkDef, routes, transforms, pipelineConfig);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid pipeline YAML", failure);
        }
    }

    private SourceDef toSourceDef(JsonNode node) {
        Map<String, Object> source = scalarOptions(node, SOURCE_KEY);
        String type = removeRequired(source, TYPE_KEY, SOURCE_KEY);
        String name = removeOptional(source, NAME_KEY);
        return new SourceDef(type, name, ReadonlyConfig.fromMap(source));
    }

    private SinkDef toSinkDef(JsonNode node) {
        Map<String, Object> sink = scalarOptions(node, SINK_KEY);
        String type = removeRequired(sink, TYPE_KEY, SINK_KEY);
        String name = removeOptional(sink, NAME_KEY);
        return new SinkDef(type, name, ReadonlyConfig.fromMap(sink));
    }

    private RouteDef toRouteDef(JsonNode node) {
        validateNodeKeys(ROUTE_KEY, node, Set.of(ROUTE_SOURCE_TABLE_KEY, ROUTE_SINK_TABLE_KEY),
                Set.of(ROUTE_REPLACE_SYMBOL, ROUTE_DESCRIPTION_KEY));
        return new RouteDef(
                requiredText(node, ROUTE_SOURCE_TABLE_KEY, ROUTE_KEY),
                requiredText(node, ROUTE_SINK_TABLE_KEY, ROUTE_KEY),
                optionalText(node, ROUTE_REPLACE_SYMBOL),
                optionalText(node, ROUTE_DESCRIPTION_KEY));
    }

    private TransformDef toTransformDef(JsonNode node) {
        validateNodeKeys(TRANSFORM_KEY, node, Set.of(TRANSFORM_SOURCE_TABLE_KEY),
                Set.of(TRANSFORM_PROJECTION_KEY, TRANSFORM_FILTER_KEY));
        return new TransformDef(
                requiredText(node, TRANSFORM_SOURCE_TABLE_KEY, TRANSFORM_KEY),
                optionalText(node, TRANSFORM_PROJECTION_KEY),
                optionalText(node, TRANSFORM_FILTER_KEY));
    }

    private ReadonlyConfig toConfiguration(JsonNode node, String section) {
        if (node == null || node.isNull()) {
            return ReadonlyConfig.fromMap(Map.of());
        }
        return ReadonlyConfig.fromMap(scalarOptions(node, section));
    }

    /**
     * Flink CDC treats connector and pipeline options as a flat key/value configuration.
     * Do not turn connector-specific keys into hard-coded pipeline parser rules.
     */
    private Map<String, Object> scalarOptions(JsonNode node, String section) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(section + " must be a YAML mapping");
        }
        Map<String, Object> options = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (!value.isValueNode() || value.isNull()) {
                throw new IllegalArgumentException(
                        section + "." + field.getKey() + " must be a scalar option");
            }
            options.put(field.getKey(), value.asText());
        }
        return options;
    }

    private static String removeRequired(Map<String, Object> values, String key, String section) {
        Object value = values.remove(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(section + "." + key + " is required");
        }
        return text;
    }

    private static String removeOptional(Map<String, Object> values, String key) {
        return (String) values.remove(key);
    }

    private static JsonNode required(JsonNode node, String field, String section) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(section + "." + field + " is required");
        }
        return value;
    }

    private static String requiredText(JsonNode node, String key, String section) {
        JsonNode value = required(node, key, section);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(section + "." + key + " must be text");
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(key + " must be text");
        }
        return value.asText();
    }

    private static void validateArray(String section, JsonNode value) {
        if (!value.isArray()) {
            throw new IllegalArgumentException(section + " must be a YAML sequence");
        }
    }

    private static void validateNodeKeys(
            String section, JsonNode node, Set<String> required, Set<String> optional) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(section + " must be a YAML mapping");
        }
        Set<String> all = new HashSet<>(required);
        all.addAll(optional);
        for (String key : required) {
            required(node, key, section);
        }
        Iterator<String> keys = node.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!all.contains(key)) {
                throw new IllegalArgumentException("Unknown " + section + " option: " + key);
            }
        }
    }
}
