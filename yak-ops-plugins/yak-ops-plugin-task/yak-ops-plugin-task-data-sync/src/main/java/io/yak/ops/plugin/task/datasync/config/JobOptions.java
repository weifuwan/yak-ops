package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** User-defined pipeline fields and system-owned execution options. */
public final class JobOptions {

    public static final Option<Map<String, Object>> SOURCE =
            Options.key("source").mapObjectType().noDefaultValue();
    public static final Option<Map<String, Object>> SINK =
            Options.key("sink").mapObjectType().noDefaultValue();
    public static final Option<List<Object>> TRANSFORM =
            Options.key("transform").listType(Object.class).defaultValue(List.of());

    public static final Option<String> TYPE = Options.key("type").stringType().noDefaultValue();
    public static final Option<String> DATASOURCE_ID =
            Options.key("datasourceId").stringType().noDefaultValue();
    public static final Option<String> DATABASE =
            Options.key("database").stringType().noDefaultValue();
    public static final Option<String> SCHEMA =
            Options.key("schema").stringType().noDefaultValue();
    public static final Option<String> TABLE = Options.key("table").stringType().noDefaultValue();

    public static final Option<Integer> SOURCE_PARALLELISM =
            Options.key("data-sync.source-parallelism").intType().defaultValue(1);
    public static final Option<Integer> SINK_PARALLELISM =
            Options.key("data-sync.sink-parallelism").intType().defaultValue(1);
    public static final Option<Duration> CHECKPOINT_INTERVAL =
            Options.key("data-sync.checkpoint-interval").durationType().defaultValue(Duration.ofSeconds(10));
    public static final Option<Integer> MAX_RESTARTS =
            Options.key("data-sync.max-restarts").intType().defaultValue(1);

    public static final OptionRule PIPELINE_RULE =
            OptionRule.builder().required(SOURCE, SINK).optional(TRANSFORM).build();

    public static final OptionRule ENDPOINT_RULE = OptionRule.builder()
            .required(TYPE, Conditions.notBlank(TYPE))
            .required(
                    DATASOURCE_ID,
                    Conditions.notBlank(DATASOURCE_ID).and(Conditions.matches(DATASOURCE_ID, "(?s)^.{1,64}$")))
            .required(TABLE, Conditions.notBlank(TABLE).and(Conditions.matches(TABLE, "(?s)^.{1,128}$")))
            .optional(DATABASE, Conditions.matches(DATABASE, "(?s)^.{0,128}$"))
            .optional(SCHEMA, Conditions.matches(SCHEMA, "(?s)^.{0,128}$"))
            .build();

    public static final OptionRule RUNTIME_RULE = OptionRule.builder()
            .optional(
                    SOURCE_PARALLELISM,
                    Conditions.greaterOrEqual(SOURCE_PARALLELISM, 1)
                            .and(Conditions.lessOrEqual(SOURCE_PARALLELISM, 16)))
            .optional(
                    SINK_PARALLELISM,
                    Conditions.greaterOrEqual(SINK_PARALLELISM, 1).and(Conditions.lessOrEqual(SINK_PARALLELISM, 16)))
            .optional(MAX_RESTARTS, Conditions.greaterOrEqual(MAX_RESTARTS, 0))
            .optional(CHECKPOINT_INTERVAL, Conditions.greaterThan(CHECKPOINT_INTERVAL, Duration.ZERO))
            .build();

    /** Returns Connector operation settings without platform routing and datasource references. */
    public static ReadonlyConfig connectorOptions(ReadonlyConfig endpoint) {
        Map<String, Object> values = new java.util.LinkedHashMap<>(endpoint.getSourceMap());
        for (Option<?> option : List.of(TYPE, DATASOURCE_ID, DATABASE, SCHEMA, TABLE)) {
            values.remove(option.key());
        }
        return ReadonlyConfig.fromMap(values);
    }

    private JobOptions() {}

    /** Defaults for single-table embedded execution. */
    public static ReadonlyConfig defaults() {
        return new Configuration()
                .set(SOURCE_PARALLELISM, 1)
                .set(SINK_PARALLELISM, 1)
                .set(CHECKPOINT_INTERVAL, Duration.ofSeconds(10))
                .set(MAX_RESTARTS, 1)
                .toReadonlyConfig();
    }
}
