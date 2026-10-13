package io.yak.ops.core.configuration;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.core.api.RuntimeExecutionMode;

/**
 * Typed options controlling how a submitted pipeline executes.
 *
 * <p>Runtime planning validates effective values and their compatibility with the topology.
 */
public final class ExecutionOptions {

    /**
     * Execution mode chosen during graph planning.
     *
     * <p>AUTOMATIC selects BATCH only when all sources are bounded; explicit BATCH rejects
     * unbounded sources.
     */
    public static final Option<RuntimeExecutionMode> RUNTIME_MODE = Options.key("execution.runtime-mode")
            .enumType(RuntimeExecutionMode.class)
            .defaultValue(RuntimeExecutionMode.AUTOMATIC);

    /**
     * Maximum whole-job restart attempts; disabled by default.
     *
     * <p>Restart is supported only from a durably completed Source-to-Sink checkpoint,
     * with fresh execution attempts for all subtasks. Partial Reader-only restart is unsupported.
     */
    public static final Option<Integer> MAX_RESTART_ATTEMPTS =
            Options.key("execution.restart.max-attempts").intType().defaultValue(0);

    /** Stable, application-supplied identity for Job logging; empty outside task execution. */
    public static final Option<String> LOG_ID =
            Options.key("execution.log-id").stringType().defaultValue("");

    /** Per-job detailed diagnostics, independent of the application logging level. */
    public static final Option<Boolean> DEBUG =
            Options.key("execution.debug").booleanType().defaultValue(false);

    private ExecutionOptions() {}
}
