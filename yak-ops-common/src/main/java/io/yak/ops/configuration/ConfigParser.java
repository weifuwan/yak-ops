package io.yak.ops.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Converts persisted JSON objects to and from the shared typed configuration container. */
public final class ConfigParser {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private ConfigParser() {}

    /** Parses a JSON object without accepting arrays, scalar values, or raw JSON in diagnostics. */
    public static ReadonlyConfig parseJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Configuration JSON is required");
        }
        try {
            Object root = MAPPER.readValue(json, Object.class);
            if (!(root instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Configuration JSON must be an object");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Configuration field name must be a string");
                }
                values.put(key, entry.getValue());
            }
            return ReadonlyConfig.fromMap(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid configuration JSON", exception);
        }
    }

    /** Serializes only the explicit values of a validated configuration. */
    public static String toJson(ReadonlyConfig config) {
        try {
            return MAPPER.writeValueAsString(
                    Objects.requireNonNull(config, "config").getSourceMap());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Configuration JSON serialization failed", exception);
        }
    }
}
