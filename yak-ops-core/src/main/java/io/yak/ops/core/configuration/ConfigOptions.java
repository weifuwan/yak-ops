package io.yak.ops.core.configuration;

import java.time.Duration;
import java.util.Objects;

/** 用于声明类型安全 {@link ConfigOption} 的链式构建工具。 */
public final class ConfigOptions {

    private ConfigOptions() {}

    public static OptionBuilder key(String key) {
        return new OptionBuilder(key);
    }

    public static final class OptionBuilder {
        private final String key;

        private OptionBuilder(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Configuration key must not be blank");
            }
            this.key = key;
        }

        public TypedBuilder<String> stringType() {
            return type(String.class);
        }

        public TypedBuilder<Integer> intType() {
            return type(Integer.class);
        }

        public TypedBuilder<Long> longType() {
            return type(Long.class);
        }

        public TypedBuilder<Boolean> booleanType() {
            return type(Boolean.class);
        }

        public TypedBuilder<Double> doubleType() {
            return type(Double.class);
        }

        public TypedBuilder<Float> floatType() {
            return type(Float.class);
        }

        public TypedBuilder<Duration> durationType() {
            return type(Duration.class);
        }

        public <E extends Enum<E>> TypedBuilder<E> enumType(Class<E> enumClass) {
            return type(Objects.requireNonNull(enumClass, "enumClass must not be null"));
        }

        private <T> TypedBuilder<T> type(Class<T> clazz) {
            return new TypedBuilder<>(key, clazz);
        }
    }

    public static final class TypedBuilder<T> {
        private final String key;
        private final Class<T> clazz;

        private TypedBuilder(String key, Class<T> clazz) {
            this.key = key;
            this.clazz = clazz;
        }

        public ConfigOption<T> defaultValue(T value) {
            Objects.requireNonNull(value, "Default value must not be null");
            return new ConfigOption<>(key, clazz, value, true);
        }

        public ConfigOption<T> noDefaultValue() {
            return new ConfigOption<>(key, clazz, null, false);
        }
    }
}
