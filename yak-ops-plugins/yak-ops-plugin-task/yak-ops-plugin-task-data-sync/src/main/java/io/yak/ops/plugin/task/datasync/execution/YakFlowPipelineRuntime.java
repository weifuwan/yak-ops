package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.client.PipelineContext;
import io.yak.ops.core.client.PipelineExecution;
import io.yak.ops.core.client.PipelinePlan;
import io.yak.ops.core.client.PipelineRuntime;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.validation.JobConfigValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles V5 definitions with the existing YakFlow execution algorithm. */
public final class YakFlowPipelineRuntime implements PipelineRuntime {
    private final JobPlanner planner;
    private final DataSyncTaskExecutor executor;
    private final FactoryUtil factories;

    public YakFlowPipelineRuntime(JobPlanner planner, DataSyncTaskExecutor executor, FactoryUtil factories) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    @Override
    public void validate(PipelineDef definition, PipelineContext context) {
        JobConfigValidator.validateConfig(definition.getOriginal(), factories);
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
    public PipelineExecution prepare(PipelineDef definition, PipelinePlan plan, PipelineContext context) {
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
