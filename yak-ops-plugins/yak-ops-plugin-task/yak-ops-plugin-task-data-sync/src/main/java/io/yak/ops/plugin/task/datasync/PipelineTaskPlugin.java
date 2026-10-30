package io.yak.ops.plugin.task.datasync;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.composer.PipelineComposer;
import io.yak.ops.core.composer.PipelineContext;
import io.yak.ops.core.composer.PipelineExecution;
import io.yak.ops.core.composer.PipelinePlan;
import io.yak.ops.core.parser.JsonPipelineDefinitionParser;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.execution.PipelineTaskExecutor;
import io.yak.ops.plugin.task.datasync.execution.PipelineExecutionTaskHandle;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.solution.Solution;
import io.yak.ops.plugin.task.datasync.solution.SolutionCatalog;
import io.yak.ops.plugin.task.datasync.validation.JobConfigValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Owns DATA_SYNC Task definition rules and delegates physical planning and execution. */
public final class PipelineTaskPlugin implements ExecutableTaskPlugin {

    public static final String TYPE = "DATA_SYNC";
    private static final Option<java.util.Map<String, Object>> SOURCE =
            Options.key("source").mapObjectType().noDefaultValue();
    private static final Option<java.util.Map<String, Object>> SINK =
            Options.key("sink").mapObjectType().noDefaultValue();
    private static final Option<List<Object>> ROUTE =
            Options.key("route").listType(Object.class).noDefaultValue();
    private static final Option<List<Object>> TRANSFORM =
            Options.key("transform").listType(Object.class).noDefaultValue();
    private static final Option<java.util.Map<String, Object>> PIPELINE =
            Options.key("pipeline").mapObjectType().noDefaultValue();
    private static final OptionRule RULE = OptionRule.builder()
            .required(SOURCE, SINK)
            .optional(ROUTE, TRANSFORM, PIPELINE)
            .build();

    private final JobPlanner planner;
    private final PipelineTaskExecutor executor;
    private final FactoryUtil factories;
    private final PipelineComposer client;

    public PipelineTaskPlugin(JobPlanner planner, PipelineTaskExecutor executor, FactoryUtil factories) {
        this(planner, executor, factories, null);
    }

    public PipelineTaskPlugin(
            JobPlanner planner, PipelineTaskExecutor executor, FactoryUtil factories, PipelineComposer client) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.client = client;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int parameterSchemaVersion() {
        return 5;
    }

    @Override
    public OptionRule optionRule() {
        return RULE;
    }

    @Override
    public ReadonlyConfig parse(String content) {
        try {
            ReadonlyConfig parsed =
                    new JsonPipelineDefinitionParser().parse(content).getOriginal();
            return JobConfigValidator.validateConfig(parsed, factories).parameters();
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid V5 DATA_SYNC pipeline JSON", failure);
        }
    }

    private static PipelineContext context(TaskExecutionIdentity identity, boolean debug) {
        return new PipelineContext(
                identity.workspaceId(),
                identity.definitionId(),
                identity.definitionVersion(),
                identity.instanceId(),
                debug);
    }

    private static TaskExecutionResources admission(PipelinePlan plan) {
        List<ExecutionResource> claims = new ArrayList<>();
        for (PipelinePlan.ResourceClaim claim : plan.resources()) {
            claims.add(new ExecutionResource(claim.type(), claim.key()));
        }
        return new TaskExecutionResources(claims, plan.resourceRevision());
    }

    /**
     * Lists supported type-level choices without accessing actual datasource connections.
     *
     * @param sourceType source product datasource type
     * @param targetType target product datasource type
     * @return immutable valid synchronization choices
     */
    public List<Solution> solutions(String sourceType, String targetType) {
        return new SolutionCatalog(factories).list(sourceType, targetType);
    }

    @Override
    public void validateUpdate(ReadonlyConfig previous, ReadonlyConfig updated, TaskValidationContext context) {
        JobConfig before = JobConfig.from(previous);
        JobConfig after = JobConfig.from(updated);
        if (before.solutionType() != after.solutionType()
                || !context.dataSource(before.sourceDataSourceId())
                        .type()
                        .equals(context.dataSource(after.sourceDataSourceId()).type())
                || !context.dataSource(before.targetDataSourceId())
                        .type()
                        .equals(context.dataSource(after.targetDataSourceId()).type())) {
            throw new TaskPluginException("Changing synchronization solution or datasource types requires a new task");
        }
    }

    @Override
    public void validateReferences(ReadonlyConfig parameters, TaskValidationContext context) {
        JobConfigValidator.validateReferences(parameters, context, factories);
    }

    @Override
    public void validate(ReadonlyConfig parameters, TaskValidationContext context) {
        JobConfigValidator.validateResources(parameters, context, factories);
    }

    @Override
    public boolean supportsScheduling(ReadonlyConfig parameters) {
        String identifier = JobConfig.from(parameters).solutionType().sourceIdentifier();
        return factories.source(identifier).boundedness() == Boundedness.BOUNDED;
    }

    @Override
    public TaskExecutionResources plan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
        if (client != null) {
            return admission(client.plan(ConfigParser.toJson(parameters), context(context, false)));
        }
        return planner.admissionPlan(parameters, context);
    }

    @Override
    public CompletionStage<TaskHandle> start(
            ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity context, boolean debug) {
        if (client == null) return executor.start(parameters, plan, context, debug);
        try {
            PipelineContext trusted = context(context, debug);
            String json = ConfigParser.toJson(parameters);
            TaskExecutionResources actual = admission(client.plan(json, trusted));
            if (!plan.equals(actual)) {
                throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
            }
            PipelineExecution job = client.compose(json, trusted);
            return CompletableFuture.completedFuture(new PipelineExecutionTaskHandle(job));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(
                    new TaskStartRejectedException("DATA_SYNC rejected before engine submission", failure));
        }
    }
}
