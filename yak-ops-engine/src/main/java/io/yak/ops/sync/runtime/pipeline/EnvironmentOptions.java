package io.yak.ops.sync.runtime.pipeline;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import java.time.Duration;

/** Application-owned execution settings; never accepted from persisted DATA_SYNC job JSON. */
public final class EnvironmentOptions {

    public static final Option<Integer> SOURCE_PARALLELISM =
            Options.key("data-sync.source-parallelism").intType().defaultValue(1);
    public static final Option<Integer> SINK_PARALLELISM =
            Options.key("data-sync.sink-parallelism").intType().defaultValue(1);
    public static final Option<Duration> CHECKPOINT_INTERVAL =
            Options.key("data-sync.checkpoint-interval").durationType().defaultValue(Duration.ofSeconds(10));
    public static final Option<Integer> MAX_RESTARTS =
            Options.key("data-sync.max-restarts").intType().defaultValue(1);

    public static final OptionRule RULE = OptionRule.builder()
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

    public static ReadonlyConfig defaults() {
        return new Configuration()
                .set(SOURCE_PARALLELISM, 1)
                .set(SINK_PARALLELISM, 1)
                .set(CHECKPOINT_INTERVAL, Duration.ofSeconds(10))
                .set(MAX_RESTARTS, 1)
                .toReadonlyConfig();
    }

    private EnvironmentOptions() {}
}
