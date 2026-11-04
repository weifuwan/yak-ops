package org.apache.flink.cdc.common.pipeline;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * Runtime execution mode of the pipeline includes STREAMING and BATCH, with the default value being
 * STREAMING. It is referenced from {@link org.apache.flink.api.common.RuntimeExecutionMode}. Among
 * other things, this controls task scheduling, network shuffle behavior, and time semantics. Some
 * operations will also change their record emission behaviour based on the configured execution
 * mode.
 */
@PublicEvolving
public enum RuntimeExecutionMode {
    /**
     * The Pipeline will be executed with Streaming Semantics. All tasks will be deployed before
     * execution starts, checkpoints will be enabled, and both processing and event time will be
     * fully supported.
     */
    STREAMING,

    /**
     * The Pipeline will be executed with Batch Semantics. Tasks will be scheduled gradually based
     * on the scheduling region they belong, shuffles between regions will be blocking, watermarks
     * are assumed to be "perfect" i.e. no late data, and processing time is assumed to not advance
     * during execution.
     */
    BATCH,
}
