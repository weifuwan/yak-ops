package io.yak.ops.core.configuration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mutable, typed configuration container for job submission and execution planning.
 *
 * <p>Values may originate from typed setters or string-valued persisted configuration.
 * Copy operations isolate the container's explicit entries. Diagnostic {@code toString()} masks commonly sensitive keys, while {@code toMap()}
 * exports original values.
 */
public class Configuration implements ReadableConfig, WritableConfig, Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("^([+-]?\\d+)\\s*(ns|us|ms|s|min|m|h|d)$", Pattern.CASE_INSENSITIVE);

    private final Map<String, Object> values = new LinkedHashMap<>();

    /** Creates an empty configuration with no explicitly stored entries. */
    public Configuration() {}

    /** Copies the explicitly configured entries into a new independent container. */
    public Configuration(Configuration other) {
        values.putAll(Objects.requireNonNull(other, "other must not be null").snapshot());
    }

    /** Creates a configuration from explicitly stored string-valued entries. */
    public static Configuration fromMap(Map<String, String> entries) {
        Configuration config = new Configuration();
        Objects.requireNonNull(entries, "entries must not be null").forEach(config::setString);
        return config;
    }

    @Override
    public synchronized <T> Configuration set(ConfigOption<T> option, T value) {
        Objects.requireNonNull(option, "option must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (!option.getClazz().isInstance(value)) {
            throw new IllegalArgumentException("Wrong value type for configuration key: " + option.key());
        }
        values.put(option.key(), value);
        return this;
    }

    @Override
    public <T> T get(ConfigOption<T> option) {
        Objects.requireNonNull(option, "option must not be null");
        return getOptional(option).orElseGet(option::defaultValue);
    }

    @Override
    public synchronized <T> Optional<T> getOptional(ConfigOption<T> option) {
        Objects.requireNonNull(option, "option must not be null");
        Object stored = values.get(option.key());
        return stored == null ? Optional.empty() : Optional.of(convert(stored, option));
    }

    /** Stores a raw string value under a nonblank key. */
    public synchronized void setString(String key, String value) {
        validateKey(key);
        values.put(key, Objects.requireNonNull(value, "value must not be null"));
    }

    /** Returns the stored value as a string, or the supplied fallback when absent. */
    public synchronized String getString(String key, String defaultValue) {
        validateKey(key);
        Object value = values.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public synchronized boolean contains(ConfigOption<?> option) {
        Objects.requireNonNull(option, "option must not be null");
        return values.containsKey(option.key());
    }

    public synchronized boolean containsKey(String key) {
        validateKey(key);
        return values.containsKey(key);
    }

    @Override
    public synchronized <T> boolean removeConfig(ConfigOption<T> option) {
        Objects.requireNonNull(option, "option must not be null");
        return values.remove(option.key()) != null;
    }

    public synchronized boolean removeKey(String key) {
        validateKey(key);
        return values.remove(key) != null;
    }

    public synchronized Set<String> keySet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
    }

    /**
 * Merges explicitly configured entries from another configuration.
 *
 * <p>Incoming entries with matching keys override the existing values; defaults are not copied.
 */
    public void addAll(Configuration other) {
        Map<String, Object> incoming =
                Objects.requireNonNull(other, "other must not be null").snapshot();
        synchronized (this) {
            values.putAll(incoming);
        }
    }

    @Override
    public synchronized Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, String.valueOf(value)));
        return result;
    }

    @Override
    public Configuration clone() {
        return new Configuration(this);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Configuration config && snapshot().equals(config.snapshot());
    }

    @Override
    public int hashCode() {
        return snapshot().hashCode();
    }

    /** Masks sensitive values in diagnostics; {@link #toMap()} intentionally does not mask them. */
    @Override
    public String toString() {
        Map<String, Object> masked = new LinkedHashMap<>();
        snapshot().forEach((key, value) -> masked.put(key, sensitive(key) ? "******" : value));
        return masked.toString();
    }

    private synchronized Map<String, Object> snapshot() {
        return new LinkedHashMap<>(values);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Configuration key must not be blank");
        }
    }

    private static boolean sensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("passwd")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("credential")
                || lower.contains("private-key")
                || lower.contains("access-key");
    }

    private static <T> T convert(Object value, ConfigOption<T> option) {
        Class<T> type = option.getClazz();
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        String text = String.valueOf(value).trim();
        Object parsed;
        try {
            if (type == String.class) {
                parsed = String.valueOf(value);
            } else if (type == Integer.class) {
                parsed = Integer.valueOf(text);
            } else if (type == Long.class) {
                parsed = Long.valueOf(text);
            } else if (type == Boolean.class) {
                if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
                    throw new IllegalArgumentException("Boolean must be true or false");
                }
                parsed = Boolean.valueOf(text);
            } else if (type == Double.class) {
                parsed = Double.valueOf(text);
            } else if (type == Float.class) {
                parsed = Float.valueOf(text);
            } else if (type == Duration.class) {
                parsed = parseDuration(text);
            } else if (type.isEnum()) {
                parsed = parseEnum(type, text);
            } else {
                throw new IllegalArgumentException("Unsupported configuration type");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid value for configuration key: " + option.key(), exception);
        }
        return type.cast(parsed);
    }

    private static <T> T parseEnum(Class<T> type, String text) {
        for (T constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Invalid enum constant");
    }

    private static Duration parseDuration(String value) {
        try {
            return Duration.parse(value.toUpperCase(Locale.ROOT));
        } catch (DateTimeParseException ignored) {
            // Also accept readable durations such as 500ms, 10s, 2min and 1h.
        }
        Matcher matcher = DURATION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Duration must be ISO-8601 or a number with a unit");
        }
        long count = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "ns" -> Duration.ofNanos(count);
            case "us" -> Duration.ofNanos(Math.multiplyExact(count, 1_000));
            case "ms" -> Duration.ofMillis(count);
            case "s" -> Duration.ofSeconds(count);
            case "m", "min" -> Duration.ofMinutes(count);
            case "h" -> Duration.ofHours(count);
            case "d" -> Duration.ofDays(count);
            default -> throw new IllegalArgumentException("Unsupported duration unit");
        };
    }
}
