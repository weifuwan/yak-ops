package io.yak.ops.core.execution;

import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.configuration.Configuration;
import java.util.concurrent.CompletableFuture;

/**
 * Unified submission entry point for bounded and unbounded pipelines.
 *
 * <p>The runtime determines the execution mode from the pipeline and its configuration.
 *
 * @author weifuwan
 */
public interface PipelineExecutor {

    /**
     * Submits a pipeline asynchronously without waiting for the job to finish.
     *
     * @param pipeline the logical pipeline to execute
     * @param configuration the effective submission configuration
     * @return a future completed with the job handle after successful submission
     */
    CompletableFuture<JobClient> execute(Pipeline pipeline, Configuration configuration);
}
