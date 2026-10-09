package io.yak.ops.core.api;

/**
 * Execution modes supported by bounded and unbounded pipelines.
 *
 * <p>The planner resolves the mode before execution and rejects {@link #BATCH} for an
 * unbounded source.
 */
public enum RuntimeExecutionMode {
    /** Selects batch or streaming execution based on the sources' boundedness. */
    AUTOMATIC,

    /** Executes a bounded pipeline with batch semantics. */
    BATCH,

    /** Executes a bounded or unbounded pipeline with streaming semantics. */
    STREAMING
}
