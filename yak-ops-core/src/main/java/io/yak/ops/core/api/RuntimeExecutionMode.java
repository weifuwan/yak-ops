package io.yak.ops.core.api;

/**
 * Runtime mode for bounded and unbounded pipelines.
 *
 * <p>The mode is resolved before a pipeline starts. The executor must reject
 * {@link #BATCH} for a pipeline containing an unbounded source.
 */
public enum RuntimeExecutionMode {
    /** Resolve the runtime mode from the boundedness of all sources. */
    AUTOMATIC,

    /** Execute a bounded pipeline with batch semantics. */
    BATCH,

    /** Execute a bounded or unbounded pipeline with streaming semantics. */
    STREAMING
}
