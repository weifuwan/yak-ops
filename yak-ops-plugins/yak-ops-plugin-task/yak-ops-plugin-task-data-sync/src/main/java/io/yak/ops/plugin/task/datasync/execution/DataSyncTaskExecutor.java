package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.datasync.pipeline.CompiledPipeline;
import io.yak.ops.plugin.task.datasync.pipeline.PipelineBuilder;
import io.yak.ops.plugin.task.datasync.plan.JobPlan;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Revalidates Task admission and submits the authorized physical pipeline to YakFlow. */
public final class DataSyncTaskExecutor {

    private final JobPlanner planner;
    private final PipelineBuilder builder;
    private final PipelineExecutor executor;

    public DataSyncTaskExecutor(JobPlanner planner, PipelineBuilder builder, PipelineExecutor executor) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.builder = Objects.requireNonNull(builder, "builder");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Submits one physical pipeline after checking its current resource binding.
     *
     * @param parameters pinned Task parameters
     * @param plan admitted resource claims and revision fingerprint
     * @param identity trusted Task execution identity
     * @param debug diagnostics mode captured for the admitted Instance
     * @return asynchronous execution handle or pre-submission rejection
     */
    public CompletionStage<TaskHandle> start(
            ReadonlyConfig parameters, TaskAdmissionPlan plan, TaskExecutionIdentity identity, boolean debug) {
        CompiledPipeline pipeline;
        try {
            JobPlan prepared = planner.plan(parameters, identity);
            if (!plan.equals(prepared.admissionPlan())) {
                throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
            }
            pipeline = builder.build(prepared, identity, debug);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(
                    new TaskStartRejectedException("DATA_SYNC rejected before engine submission", error));
        }

        try {
            return executor.execute(pipeline.graph(), pipeline.configuration())
                    .thenApply(client -> (TaskHandle) new YakFlowTaskHandle(client, pipeline.progress()));
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
