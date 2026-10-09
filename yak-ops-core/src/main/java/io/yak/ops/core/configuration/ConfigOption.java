package io.yak.ops.core.configuration;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Typed configuration-key contract with an optional default value.
 *
 * <p>The option describes how to interpret a value; it does not store runtime configuration.
 *
 * @param <T> the option's Java value type
 */
public final class ConfigOption<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String key;
    private final Class<T> clazz;
    private final T defaultValue;
    private final boolean hasDefaultValue;

    ConfigOption(String key, Class<T> clazz, T defaultValue, boolean hasDefaultValue) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Configuration key must not be blank");
        }
        this.key = key;
        this.clazz = Objects.requireNonNull(clazz, "Configuration type must not be null");
        if (hasDefaultValue && !clazz.isInstance(defaultValue)) {
            throw new IllegalArgumentException("Default value does not match configuration type: " + key);
        }
        this.defaultValue = defaultValue;
        this.hasDefaultValue = hasDefaultValue;
    }

    public String key() {
        return key;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public boolean hasDefaultValue() {
        return hasDefaultValue;
    }
}
