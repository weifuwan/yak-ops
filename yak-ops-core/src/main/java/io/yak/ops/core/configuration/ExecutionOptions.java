package io.yak.ops.core.configuration;

import io.yak.ops.core.api.RuntimeExecutionMode;
import java.time.Duration;

/**
 * Typed options controlling how a submitted pipeline is executed.
 *
 * <p>This class declares options only. Runtime preparation is responsible for
 * validating value ranges and compatibility with the pipeline.
 */
public final class ExecutionOptions {

    /**
     * Execution mode. AUTOMATIC chooses BATCH when all sources are bounded,
     * otherwise STREAMING. An explicit BATCH mode requires bounded sources.
     */
    public static final ConfigOption<RuntimeExecutionMode> RUNTIME_MODE =
            ConfigOptions.key("execution.runtime-mode")
                    .enumType(RuntimeExecutionMode.class)
                    .defaultValue(RuntimeExecutionMode.AUTOMATIC);

    /**
     * Default parallelism for operators without an explicit parallelism.
     * The resolved value must be greater than zero.
     */
    public static final ConfigOption<Integer> DEFAULT_PARALLELISM =
            ConfigOptions.key("parallelism.default")
                    .intType()
                    .defaultValue(1);

    /**
     * Interval between periodic checkpoints. Duration.ZERO disables periodic
     * checkpoints. A negative duration is invalid. Enabling an interval alone
     * does not imply durable recovery or end-to-end exactly-once semantics.
     */
    public static final ConfigOption<Duration> CHECKPOINT_INTERVAL =
            ConfigOptions.key("execution.checkpointing.interval")
                    .durationType()
                    .defaultValue(Duration.ZERO);

    private ExecutionOptions() {}
}
