package io.yak.ops.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared conversion and defensive copying for mutable and immutable configurations. */
final class ConfigValues {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private ConfigValues() {}

    static <T> T convertStructured(Object value, Option<T> option) {
        try {
            return MAPPER.convertValue(value, option.valueType());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid value for configuration key: " + option.key(), exception);
        }
    }

    static Map<String, Object> copyMap(Map<String, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null) {
                throw new IllegalArgumentException("Configuration key must not be null");
            }
            values.put(key, copy(value));
        });
        return Collections.unmodifiableMap(values);
    }

    static Object copy(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> converted = new LinkedHashMap<>();
            source.forEach((key, member) -> {
                if (!(key instanceof String name)) {
                    throw new IllegalArgumentException("Configuration map keys must be strings");
                }
                converted.put(name, copy(member));
            });
            return Collections.unmodifiableMap(converted);
        }
        if (value instanceof List<?> values) {
            List<Object> copy = new ArrayList<>(values.size());
            for (Object item : values) {
                copy.add(copy(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Object[] values) {
            List<Object> copy = new ArrayList<>(values.length);
            for (Object item : values) {
                copy.add(copy(item));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    static String diagnostic(Map<String, ?> values) {
        return masked(values).toString();
    }

    private static Map<String, Object> masked(Map<String, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String name = key.toLowerCase(Locale.ROOT);
            boolean sensitive = name.equals("connection")
                    || name.startsWith("connection.")
                    || name.contains("passphrase")
                    || name.contains("password")
                    || name.contains("passwd")
                    || name.contains("secret")
                    || name.contains("token")
                    || name.contains("credential")
                    || name.contains("private-key")
                    || name.contains("privatekey")
                    || name.contains("access-key")
                    || name.contains("accesskey");
            if (sensitive) {
                result.put(key, "******");
            } else if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedValues = new LinkedHashMap<>();
                nested.forEach((k, v) -> {
                    if (k instanceof String field) {
                        nestedValues.put(field, v);
                    }
                });
                result.put(key, masked(nestedValues));
            } else if (value instanceof List<?>) {
                result.put(key, "[redacted]");
            } else {
                result.put(key, value);
            }
        });
        return result;
    }
}
