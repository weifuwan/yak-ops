
package io.yak.ops.core.configuration;

import java.time.Duration;

/**
 * Configuration options controlling periodic checkpoints.
 *
 * <p>This class only declares typed options. The execution engine
 * validates their values and applies them to the checkpoint coordinator.
 *
 * <p>Inspired by Apache Flink's CheckpointingOptions.
 *
 * @author weifuwan
 */
public final class CheckpointingOptions {

    /**
     * Base interval between periodic checkpoint triggers.
     *
     * <p>Unset or Duration.ZERO disables periodic checkpoints.
     * A negative value is invalid.
     */
    public static final ConfigOption<Duration> CHECKPOINTING_INTERVAL =
            ConfigOptions.key("execution.checkpointing.interval")
                    .durationType()
                    .noDefaultValue();

    /**
     * Maximum time allowed for one checkpoint attempt.
     * A checkpoint exceeding this limit should be discarded.
     */
    public static final ConfigOption<Duration> CHECKPOINTING_TIMEOUT =
            ConfigOptions.key("execution.checkpointing.timeout")
                    .durationType()
                    .defaultValue(Duration.ofMinutes(10));

    /**
     * Minimum pause between checkpoint attempts.
     *
     * <p>With one concurrent checkpoint this guarantees
     * an idle period between attempts.
     */
    public static final ConfigOption<Duration> MIN_PAUSE_BETWEEN_CHECKPOINTS =
            ConfigOptions.key("execution.checkpointing.min-pause")
                    .durationType()
                    .defaultValue(Duration.ZERO);

    /**
     * Maximum number of checkpoint attempts in progress.
     *
     * <p>An engine supporting only one in-flight checkpoint
     * must reject values greater than one.
     */
    public static final ConfigOption<Integer> MAX_CONCURRENT_CHECKPOINTS =
            ConfigOptions.key("execution.checkpointing.max-concurrent-checkpoints")
                    .intType()
                    .defaultValue(1);

    private CheckpointingOptions() {}
}
