package io.yak.ops.composer.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import java.util.Map;
import java.util.Objects;

/** Non-secret, per-table JDBC incremental selection. Cursor progress is never stored in task parameters. */
public final class IncrementalConfig {
    private static final Option<Mode> MODE =
            Options.key("mode").enumType(Mode.class).noDefaultValue();
    private static final Option<String> COLUMN =
            Options.key("column").stringType().noDefaultValue();
    private static final Option<String> INITIAL_VALUE =
            Options.key("initialValue").stringType().noDefaultValue();
    private static final Option<Long> DELAY_SECONDS =
            Options.key("delaySeconds").longType().noDefaultValue();
    private static final Option<Long> LOOKBACK_SECONDS =
            Options.key("lookbackSeconds").longType().noDefaultValue();

    public static final OptionRule RULE = OptionRule.builder()
            .required(MODE, COLUMN)
            .optional(INITIAL_VALUE, DELAY_SECONDS, LOOKBACK_SECONDS)
            .build();

    private final Mode mode;
    private final String column;
    private final String initialValue;
    private final long delaySeconds;
    private final long lookbackSeconds;

    @JsonCreator
    public IncrementalConfig(
            @JsonProperty("mode") Mode mode,
            @JsonProperty("column") String column,
            @JsonProperty("initialValue") String initialValue,
            @JsonProperty("delaySeconds") long delaySeconds,
            @JsonProperty("lookbackSeconds") long lookbackSeconds) {
        Objects.requireNonNull(mode, "mode");
        if (column == null || column.isBlank() || column.length() > 128) {
            throw new IllegalArgumentException("Incremental column must name an existing table column");
        }
        if (delaySeconds < 0 || delaySeconds > 86_400 || lookbackSeconds < 0 || lookbackSeconds > 604_800) {
            throw new IllegalArgumentException("Invalid incremental delay or lookback");
        }
        if (mode == Mode.INCREASING && (delaySeconds != 0 || lookbackSeconds != 0)) {
            throw new IllegalArgumentException("Increasing cursors do not use timestamp delay or lookback");
        }
        if (initialValue != null && initialValue.length() > 64) {
            throw new IllegalArgumentException("Incremental initial cursor is too long");
        }
        if (initialValue != null && !initialValue.isBlank()) {
            try {
                if (mode == Mode.TIMESTAMP) java.time.LocalDateTime.parse(initialValue);
                else new java.math.BigInteger(initialValue);
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("Invalid incremental initial cursor", failure);
            }
        }
        this.mode = mode;
        this.column = column;
        this.initialValue = initialValue;
        this.delaySeconds = delaySeconds;
        this.lookbackSeconds = lookbackSeconds;
    }

    @JsonProperty("mode")
    public Mode mode() {
        return mode;
    }

    @JsonProperty("column")
    public String column() {
        return column;
    }

    @JsonProperty("initialValue")
    public String initialValue() {
        return initialValue;
    }

    @JsonProperty("delaySeconds")
    public long delaySeconds() {
        return delaySeconds;
    }

    @JsonProperty("lookbackSeconds")
    public long lookbackSeconds() {
        return lookbackSeconds;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof IncrementalConfig other)) return false;
        return java.util.Objects.equals(mode, other.mode)
                && java.util.Objects.equals(column, other.column)
                && java.util.Objects.equals(initialValue, other.initialValue)
                && java.util.Objects.equals(delaySeconds, other.delaySeconds)
                && java.util.Objects.equals(lookbackSeconds, other.lookbackSeconds);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(mode, column, initialValue, delaySeconds, lookbackSeconds);
    }

    public enum Mode {
        TIMESTAMP,
        INCREASING
    }

    /** Parses public incremental options using the component-owned rule. */
    public static IncrementalConfig from(Map<?, ?> values) {
        for (Object key : values.keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("Incremental configuration keys must be strings");
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) values;
        ReadonlyConfig options = ReadonlyConfig.fromMap(fields);
        ConfigValidator.of(options).validate(RULE);
        ConfigValidator.validateUnknownKeys(options, RULE, "source.tables.incremental");
        // Do not widen the V4 JSON string contract via Jackson's scalar coercion.
        if (!(fields.get(COLUMN.key()) instanceof String)
                || (fields.containsKey(INITIAL_VALUE.key()) && !(fields.get(INITIAL_VALUE.key()) instanceof String))) {
            throw new IllegalArgumentException("Incremental column and initial value must be text");
        }
        return new IncrementalConfig(
                options.get(MODE),
                options.get(COLUMN),
                options.get(INITIAL_VALUE),
                seconds(fields.get(DELAY_SECONDS.key())),
                seconds(fields.get(LOOKBACK_SECONDS.key())));
    }

    private static long seconds(Object value) {
        if (value == null) return 0;
        if (!(value instanceof Number number))
            throw new IllegalArgumentException("Incremental seconds must be numeric");
        long result = number.longValue();
        if (number.doubleValue() != (double) result)
            throw new IllegalArgumentException("Incremental seconds must be integral");
        return result;
    }
}
