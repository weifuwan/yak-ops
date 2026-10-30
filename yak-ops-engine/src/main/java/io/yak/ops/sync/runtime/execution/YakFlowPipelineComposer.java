package io.yak.ops.sync.runtime.execution;

import io.yak.ops.core.composer.PipelineComposer;
import io.yak.ops.core.composer.PipelineContext;
import io.yak.ops.core.composer.PipelineExecution;
import io.yak.ops.core.composer.PipelinePlan;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.sync.runtime.pipeline.PipelineBuilder;
import io.yak.ops.sync.runtime.plan.JobPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Composes Flink CDC 3.7 Pipeline definitions with the existing YakFlow execution algorithm. */
public final class YakFlowPipelineComposer implements PipelineComposer {
    private final JobPlanner planner;
    private final PipelineBuilder builder;
    private final PipelineExecutor executor;
    private final IncrementalBatchPreparation incrementalPreparation;

    public YakFlowPipelineComposer(
            JobPlanner planner,
            PipelineBuilder builder,
            PipelineExecutor executor,
            IncrementalBatchPreparation incrementalPreparation) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.builder = Objects.requireNonNull(builder, "builder");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.incrementalPreparation = Objects.requireNonNull(incrementalPreparation, "incrementalPreparation");
    }

    @Override
    public PipelinePlan plan(PipelineDef definition, PipelineContext context) {
        TaskExecutionResources resources = planner.admissionPlan(definition, identity(context));
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
        List<ExecutionResource> claims = new ArrayList<>();
        for (PipelinePlan.ResourceClaim resource : plan.resources()) {
            claims.add(new ExecutionResource(resource.type(), resource.key()));
        }
        TaskExecutionResources admission = new TaskExecutionResources(claims, plan.resourceRevision());
        return new YakFlowPipelineExecution(
                definition,
                admission,
                identity(context),
                context.debug(),
                planner,
                builder,
                executor,
                incrementalPreparation);
    }

    private static TaskExecutionIdentity identity(PipelineContext context) {
        return new TaskExecutionIdentity(
                context.workspaceId(), context.definitionId(), context.definitionVersion(), context.instanceId());
    }
}
