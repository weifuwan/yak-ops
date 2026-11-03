package io.yak.ops.configuration;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable view of typed configuration values.
 *
 * <p>All nested maps and collections are copied when the view is created. Defaults do not
 * count as explicit configuration entries. Do not log or persist values returned by getSourceMap
 * without applying the owning plugin's credential policy.
 */
public final class ReadonlyConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> values;

    private ReadonlyConfig(Map<String, ?> values) {
        this.values = ConfigValues.copyMap(values);
    }

    public static ReadonlyConfig fromMap(Map<String, ?> values) {
        return new ReadonlyConfig(Objects.requireNonNull(values, "values"));
    }

    public static ReadonlyConfig fromConfiguration(Configuration configuration) {
        return Objects.requireNonNull(configuration, "configuration").toReadonlyConfig();
    }

    public <T> T get(Option<T> option) {
        return getOptional(option).orElseGet(option::defaultValue);
    }

    public <T> Optional<T> getOptional(Option<T> option) {
        Objects.requireNonNull(option, "option");
        Object raw = value(option.key());
        return raw == null ? Optional.empty() : Optional.of(Configuration.convert(raw, option));
    }

    public boolean contains(Option<?> option) {
        return value(Objects.requireNonNull(option, "option").key()) != null;
    }

    public boolean containsKey(String key) {
        return value(key) != null;
    }

    /** Returns an immutable snapshot, including explicit nested configuration entries. */
    public Map<String, Object> getSourceMap() {
        return values;
    }

    public Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, String.valueOf(value)));
        return result;
    }

    private Object value(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Configuration key must not be blank");
        }
        if (values.containsKey(key)) {
            return values.get(key);
        }
        Object current = values;
        for (String segment : key.split("\\.")) {
            if (!(current instanceof Map<?, ?> mapping)) {
                return null;
            }
            current = mapping.get(segment);
        }
        return current;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReadonlyConfig config && values.equals(config.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return ConfigValues.diagnostic(values);
    }
}
