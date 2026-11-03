package io.yak.ops.configuration;

import com.fasterxml.jackson.databind.JavaType;
import java.util.List;
import java.util.Objects;

/**
 * Describes the type, default, and metadata of a configuration value.
 *
 * <p>An Option never holds the value of a particular Job or connection.
 *
 * @param <T> Java type of the option
 */
public class Option<T> {

    private final String key;
    private final JavaType valueType;
    private final T defaultValue;
    private final boolean hasDefaultValue;
    private final String description;
    private final List<T> allowedValues;

    Option(String key, JavaType valueType, T defaultValue, boolean hasDefaultValue) {
        this(key, valueType, defaultValue, hasDefaultValue, "", List.of());
    }

    Option(
            String key,
            JavaType valueType,
            T defaultValue,
            boolean hasDefaultValue,
            String description,
            List<T> allowedValues) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Configuration key must not be blank");
        }
        this.key = key;
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        if (hasDefaultValue && defaultValue == null) {
            throw new NullPointerException("Default value must not be null");
        }
        if (hasDefaultValue && !valueType.getRawClass().isInstance(defaultValue)) {
            throw new IllegalArgumentException("Default value does not match configuration type: " + key);
        }
        this.defaultValue = defaultValue;
        this.hasDefaultValue = hasDefaultValue;
        this.description = Objects.requireNonNull(description, "description");
        this.allowedValues = List.copyOf(allowedValues);
        if (hasDefaultValue && !this.allowedValues.isEmpty() && !this.allowedValues.contains(defaultValue)) {
            throw new IllegalArgumentException("Default value is not an allowed choice: " + key);
        }
    }

    /** Stable configuration key. */
    public String key() {
        return key;
    }

    /** Complete type, including collection element types. */
    public JavaType valueType() {
        return valueType;
    }

    @SuppressWarnings("unchecked")
    public Class<T> getClazz() {
        return (Class<T>) valueType.getRawClass();
    }

    public T defaultValue() {
        return defaultValue;
    }

    public boolean hasDefaultValue() {
        return hasDefaultValue;
    }

    public String getDescription() {
        return description;
    }

    public List<T> allowedValues() {
        return allowedValues;
    }

    public Option<T> withDescription(String description) {
        return new Option<>(key, valueType, defaultValue, hasDefaultValue, description, allowedValues);
    }

    public boolean hasStructuredType() {
        return valueType.isCollectionLikeType() || valueType.isMapLikeType();
    }
}
