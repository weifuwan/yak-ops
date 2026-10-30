package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.client.DataSyncContext;
import io.yak.ops.core.client.DataSyncEngine;
import io.yak.ops.core.client.DataSyncJob;
import io.yak.ops.core.client.DataSyncPlan;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.datasync.config.V5PipelineAdapter;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.validation.JobConfigValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bridges the Core client to the existing YakFlow-backed synchronization implementation. */
public final class YakFlowDataSyncEngine implements DataSyncEngine {
    private final JobPlanner planner;
    private final DataSyncTaskExecutor executor;
    private final FactoryUtil factories;

    public YakFlowDataSyncEngine(JobPlanner planner, DataSyncTaskExecutor executor, FactoryUtil factories) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    @Override
    public void validate(PipelineDef definition, DataSyncContext context) {
        JobConfigValidator.validateConfig(V5PipelineAdapter.toV4(definition), factories);
    }

    @Override
    public DataSyncPlan plan(PipelineDef definition, DataSyncContext context) {
        TaskExecutionResources resources = planner.admissionPlan(
                V5PipelineAdapter.toV4(definition), identity(context));
        List<DataSyncPlan.ResourceClaim> claims = new ArrayList<>();
        for (ExecutionResource resource : resources.exclusiveResources()) {
            claims.add(new DataSyncPlan.ResourceClaim(resource.type(), resource.key()));
        }
        return new DataSyncPlan(claims, resources.resourceRevision());
    }

    @Override
    public DataSyncJob prepare(PipelineDef definition, DataSyncPlan plan, DataSyncContext context) {
        ReadonlyConfig parameters = V5PipelineAdapter.toV4(definition);
        List<ExecutionResource> claims = new ArrayList<>();
        for (DataSyncPlan.ResourceClaim resource : plan.resources()) {
            claims.add(new ExecutionResource(resource.type(), resource.key()));
        }
        TaskExecutionResources admission = new TaskExecutionResources(claims, plan.resourceRevision());
        TaskHandle handle = executor.start(parameters, admission, identity(context), context.debug())
                .toCompletableFuture().join();
        return new YakFlowDataSyncJob(context.instanceId(), handle);
    }

    private static TaskExecutionIdentity identity(DataSyncContext context) {
        return new TaskExecutionIdentity(context.workspaceId(), context.definitionId(),
                context.definitionVersion(), context.instanceId());
    }
}
