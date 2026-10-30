package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.composer.PipelineComposer;
import io.yak.ops.core.composer.PipelineContext;
import io.yak.ops.core.composer.PipelineExecution;
import io.yak.ops.core.composer.PipelinePlan;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles V5 definitions with the existing YakFlow execution algorithm. */
public final class YakFlowPipelineComposer implements PipelineComposer {
    private final JobPlanner planner;
    private final PipelineTaskExecutor executor;

    public YakFlowPipelineComposer(JobPlanner planner, PipelineTaskExecutor executor) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public PipelinePlan plan(PipelineDef definition, PipelineContext context) {
        TaskExecutionResources resources = planner.admissionPlan(definition.getOriginal(), identity(context));
        List<PipelinePlan.ResourceClaim> claims = new ArrayList<>();
        for (ExecutionResource resource : resources.exclusiveResources()) {
            claims.add(new PipelinePlan.ResourceClaim(resource.type(), resource.key()));
        }
        return new PipelinePlan(claims, resources.resourceRevision());
    }

    @Override
    public PipelineExecution compose(PipelineDef definition, PipelinePlan plan, PipelineContext context) {
        // The plan was admitted by plan() and checked against the Task scheduler's claim.
        // Physical execution revalidates the metadata before any write or DDL.
        ReadonlyConfig parameters = definition.getOriginal();
        List<ExecutionResource> claims = new ArrayList<>();
        for (PipelinePlan.ResourceClaim resource : plan.resources()) {
            claims.add(new ExecutionResource(resource.type(), resource.key()));
        }
        TaskExecutionResources admission = new TaskExecutionResources(claims, plan.resourceRevision());
        TaskHandle handle = executor.start(parameters, admission, identity(context), context.debug())
                .toCompletableFuture()
                .join();
        return new YakFlowPipelineExecution(context.instanceId(), handle);
    }

    private static TaskExecutionIdentity identity(PipelineContext context) {
        return new TaskExecutionIdentity(
                context.workspaceId(), context.definitionId(), context.definitionVersion(), context.instanceId());
    }
}
