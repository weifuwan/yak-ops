package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;

/** Physical MySQL CDC snapshot and backpressure settings owned by the Connector. */
public final class MySqlCdcSourceOptions {

    public static final Option<Integer> SERVER_ID =
            Options.key("connector.mysql-cdc.server-id").intType().noDefaultValue();
    public static final Option<String> TOPIC_PREFIX =
            Options.key("connector.mysql-cdc.topic-prefix").stringType().noDefaultValue();

    public static final Option<Integer> SNAPSHOT_CHUNK_SIZE =
            Options.key("data-sync.mysql-cdc.snapshot-chunk-size").intType().defaultValue(1024);

    public static final Option<String> CHUNK_KEY_COLUMN = Options.key("data-sync.mysql-cdc.snapshot-chunk-key-column")
            .stringType()
            .noDefaultValue();

    /** Explicit opt-in for the Hybrid keyless source; the standalone Binlog source remains gated. */
    public static final Option<Boolean> KEYLESS_ENABLED =
            Options.key("data-sync.mysql-cdc.keyless.enabled").booleanType().defaultValue(false);

    public static final Option<Integer> QUEUE_CAPACITY =
            Options.key("data-sync.mysql-cdc.queue-capacity").intType().defaultValue(256);

    public static final OptionRule PHYSICAL_RULE = OptionRule.builder()
            .optional(
                    SNAPSHOT_CHUNK_SIZE,
                    Conditions.greaterOrEqual(SNAPSHOT_CHUNK_SIZE, 1)
                            .and(Conditions.lessOrEqual(SNAPSHOT_CHUNK_SIZE, 100_000)))
            .optional(
                    QUEUE_CAPACITY,
                    Conditions.greaterOrEqual(QUEUE_CAPACITY, 1).and(Conditions.lessOrEqual(QUEUE_CAPACITY, 10_000)))
            .build();

    private MySqlCdcSourceOptions() {}
}
