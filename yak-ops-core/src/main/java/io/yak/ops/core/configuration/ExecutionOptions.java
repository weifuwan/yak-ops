package io.yak.ops.core.configuration;

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
    public static final ConfigOption<RuntimeExecutionMode> RUNTIME_MODE = ConfigOptions.key("execution.runtime-mode")
            .enumType(RuntimeExecutionMode.class)
            .defaultValue(RuntimeExecutionMode.AUTOMATIC);

    /**
    * Maximum whole-job restart attempts; disabled by default.
    *
    * <p>Restart is supported only from a durably completed Source-to-Sink checkpoint,
    * with fresh execution attempts for all subtasks. Partial Reader-only restart is unsupported.
    */
    public static final ConfigOption<Integer> MAX_RESTART_ATTEMPTS =
            ConfigOptions.key("execution.restart.max-attempts").intType().defaultValue(0);

    private ExecutionOptions() {}
}
