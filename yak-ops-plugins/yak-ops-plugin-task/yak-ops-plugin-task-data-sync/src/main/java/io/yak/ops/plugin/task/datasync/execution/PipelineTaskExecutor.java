package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.datasync.pipeline.PipelineBuilder;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Creates one deferred DATA_SYNC execution handle for every supported synchronization mode.
 *
 * <p>The platform registers the handle before starting physical work. The handle owns
 * re-admission, optional schema and cursor preparation, graph submission and cancellation.
 */
public final class PipelineTaskExecutor {
    private final JobPlanner planner;
    private final PipelineBuilder builder;
    private final PipelineExecutor executor;
    private final IncrementalBatchPreparation incrementalPreparation;

    public PipelineTaskExecutor(
            JobPlanner planner,
            PipelineBuilder builder,
            PipelineExecutor executor,
            IncrementalBatchPreparation incrementalPreparation) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.builder = Objects.requireNonNull(builder, "builder");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.incrementalPreparation = Objects.requireNonNull(incrementalPreparation, "incrementalPreparation");
    }

    /**
     * Returns the same deferred TaskHandle for bounded, continuous and schema-prepared jobs.
     *
     * @param parameters validated V5 PipelineDef JSON
     * @param plan admitted resource claims
     * @param identity trusted Task Instance identity
     * @param debug Instance diagnostics flag
     */
    public CompletionStage<TaskHandle> start(
            ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity identity, boolean debug) {
        // The Task plugin has already rechecked this admission against current metadata.
        // The deferred handle independently verifies it before any physical side effects.
        return CompletableFuture.completedFuture(new PipelineTaskHandle(
                parameters, plan, identity, debug, planner, builder, executor, incrementalPreparation));
    }
}
