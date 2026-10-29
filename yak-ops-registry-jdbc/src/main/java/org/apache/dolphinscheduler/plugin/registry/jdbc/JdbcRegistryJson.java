package org.apache.dolphinscheduler.plugin.registry.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal adaptation for the upstream Registry DTOs, which only require
 * the two JSON operations from DolphinScheduler's larger common JSONUtils.
 */
public final class JdbcRegistryJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JdbcRegistryJson() {}

    public static <T> T parseObject(String json, Class<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid JDBC Registry JSON", failure);
        }
    }

    public static String toJsonString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Unable to serialize JDBC Registry JSON", failure);
        }
    }
}
