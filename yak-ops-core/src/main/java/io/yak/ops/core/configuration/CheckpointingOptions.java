package io.yak.ops.core.configuration;

import java.time.Duration;

/**
 * Typed configuration options for aligned checkpoint execution.
 *
 * <p>The runtime validates effective values and applies them to the checkpoint
 * coordinator. This class only defines the option contracts.
 *
 * @author weifuwan
 */
public final class CheckpointingOptions {

    /**
     * Base interval between periodic checkpoint triggers.
     *
     * <p>An absent or zero interval disables periodic triggering; negative intervals are invalid.
     */
    public static final ConfigOption<Duration> CHECKPOINTING_INTERVAL =
            ConfigOptions.key("execution.checkpointing.interval").durationType().defaultValue(Duration.ZERO);

    /** Maximum duration of one checkpoint attempt before it must be declined. */
    public static final ConfigOption<Duration> CHECKPOINTING_TIMEOUT =
            ConfigOptions.key("execution.checkpointing.timeout").durationType().defaultValue(Duration.ofMinutes(10));

    /**
     * Minimum idle time between successive checkpoint attempts.
     *
     * <p>Only one checkpoint may be in flight in the current local runtime.
     */
    public static final ConfigOption<Duration> MIN_PAUSE_BETWEEN_CHECKPOINTS = ConfigOptions.key(
                    "execution.checkpointing.min-pause")
            .durationType()
            .defaultValue(Duration.ZERO);

    /** Maximum concurrent checkpoints; the current runtime requires exactly one. */
    public static final ConfigOption<Integer> MAX_CONCURRENT_CHECKPOINTS = ConfigOptions.key(
                    "execution.checkpointing.max-concurrent-checkpoints")
            .intType()
            .defaultValue(1);

    /**
     * Local directory for durably completed checkpoints.
     *
     * <p>Periodic checkpointing requires an explicit directory. Separate jobs must not write
     * concurrently to the same directory; the runtime uses an exclusive lock and atomic replacement.
     */
    public static final ConfigOption<String> STATE_DIRECTORY = ConfigOptions.key(
                    "execution.checkpointing.state-directory")
            .stringType()
            .noDefaultValue();

    /**
     * Whether to resume from the most recently completed checkpoint in the state directory.
     *
     * <p>Restoration requires stable operator UIDs and a compatible topology and parallelism.
     */
    public static final ConfigOption<Boolean> RESTORE_LATEST = ConfigOptions.key(
                    "execution.checkpointing.restore-latest")
            .booleanType()
            .defaultValue(false);

    private CheckpointingOptions() {}
}
