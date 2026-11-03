package io.yak.ops.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Factory for declaring strongly typed configuration options. */
public final class Options {

    private static final TypeFactory TYPES = TypeFactory.defaultInstance();

    private Options() {}

    public static OptionBuilder key(String key) {
        return new OptionBuilder(key);
    }

    /** Selects the Java type of an option. */
    public static final class OptionBuilder {
        private final String key;

        private OptionBuilder(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Configuration key must not be blank");
            }
            this.key = key;
        }

        public TypedOptionBuilder<String> stringType() {
            return type(String.class);
        }

        public TypedOptionBuilder<Integer> intType() {
            return type(Integer.class);
        }

        public TypedOptionBuilder<Long> longType() {
            return type(Long.class);
        }

        public TypedOptionBuilder<Boolean> booleanType() {
            return type(Boolean.class);
        }

        public TypedOptionBuilder<Double> doubleType() {
            return type(Double.class);
        }

        public TypedOptionBuilder<Float> floatType() {
            return type(Float.class);
        }

        public TypedOptionBuilder<BigDecimal> bigDecimalType() {
            return type(BigDecimal.class);
        }

        public TypedOptionBuilder<Duration> durationType() {
            return type(Duration.class);
        }

        public <T extends Enum<T>> TypedOptionBuilder<T> enumType(Class<T> enumClass) {
            return type(Objects.requireNonNull(enumClass, "enumClass"));
        }

        public TypedOptionBuilder<List<String>> listType() {
            return type(new TypeReference<List<String>>() {});
        }

        public <T> TypedOptionBuilder<List<T>> listType(Class<T> elementClass) {
            Objects.requireNonNull(elementClass, "elementClass");
            return new TypedOptionBuilder<>(key, TYPES.constructCollectionType(List.class, elementClass));
        }

        public TypedOptionBuilder<Map<String, String>> mapType() {
            return type(new TypeReference<Map<String, String>>() {});
        }

        public TypedOptionBuilder<Map<String, Object>> mapObjectType() {
            return type(new TypeReference<Map<String, Object>>() {});
        }

        public <T> TypedOptionBuilder<T> type(Class<T> clazz) {
            return new TypedOptionBuilder<>(key, TYPES.constructType(Objects.requireNonNull(clazz, "clazz")));
        }

        public <T> TypedOptionBuilder<T> type(TypeReference<T> reference) {
            return new TypedOptionBuilder<>(key, TYPES.constructType(Objects.requireNonNull(reference, "reference")));
        }

        public <T> SingleChoiceOptionBuilder<T> singleChoice(Class<T> clazz, List<T> values) {
            return new SingleChoiceOptionBuilder<>(key, TYPES.constructType(clazz), values);
        }
    }

    /** Builds an option with or without an explicit default value. */
    public static class TypedOptionBuilder<T> {
        private final String key;
        private final JavaType type;

        private TypedOptionBuilder(String key, JavaType type) {
            this.key = key;
            this.type = type;
        }

        public Option<T> defaultValue(T value) {
            return new Option<>(key, type, Objects.requireNonNull(value, "value"), true);
        }

        public Option<T> noDefaultValue() {
            return new Option<>(key, type, null, false);
        }
    }

    /** Builds an option whose values must belong to a fixed set. */
    public static final class SingleChoiceOptionBuilder<T> {
        private final String key;
        private final JavaType type;
        private final List<T> values;

        private SingleChoiceOptionBuilder(String key, JavaType type, List<T> values) {
            this.key = key;
            this.type = type;
            this.values = List.copyOf(values);
            if (this.values.isEmpty()) {
                throw new IllegalArgumentException("Single-choice option requires allowed values");
            }
        }

        public SingleChoiceOption<T> defaultValue(T value) {
            return new SingleChoiceOption<>(key, type, Objects.requireNonNull(value, "value"), true, values);
        }

        public SingleChoiceOption<T> noDefaultValue() {
            return new SingleChoiceOption<>(key, type, null, false, values);
        }
    }
}
