package io.yak.ops.sync.runtime;

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
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.sync.runtime.config.JobConfig;
import io.yak.ops.sync.runtime.validation.JobConfigValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified V5 data synchronization definition, admission, and deferred submission service.
 * Task plugins only delegate to this service; engine configuration and planning stay in runtime.
 */
public final class PipelineService {
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


    private final FactoryUtil factories;
    private final PipelineComposer composer;

    public PipelineService(FactoryUtil factories, PipelineComposer composer) {
        this.factories = Objects.requireNonNull(factories, "factories");
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    public OptionRule optionRule() {
        return RULE;
    }

    public ReadonlyConfig parse(String content) {
        try {
            ReadonlyConfig parsed = new JsonPipelineDefinitionParser().parse(content).getOriginal();
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

    public void validateReferences(ReadonlyConfig parameters, TaskValidationContext context) {
        JobConfigValidator.validateReferences(parameters, context, factories);
    }

    public void validate(ReadonlyConfig parameters, TaskValidationContext context) {
        JobConfigValidator.validateResources(parameters, context, factories);
    }

    public boolean supportsScheduling(ReadonlyConfig parameters) {
        String identifier = JobConfig.from(parameters).solutionType().sourceIdentifier();
        return factories.source(identifier).boundedness() == Boundedness.BOUNDED;
    }

    public TaskExecutionResources plan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
        return admission(composer.plan(
                new JsonPipelineDefinitionParser().parse(ConfigParser.toJson(parameters)), context(context, false)));
    }

    public PipelineExecution submit(
            ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity context, boolean debug) {
        try {
            PipelineContext trusted = context(context, debug);
            io.yak.ops.core.definition.PipelineDef definition =
                    new JsonPipelineDefinitionParser().parse(ConfigParser.toJson(parameters));
            PipelinePlan admitted = composer.plan(definition, trusted);
            if (!plan.equals(admission(admitted))) {
                throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
            }
            return composer.compose(definition, admitted, trusted);
        } catch (RuntimeException failure) {
            throw new TaskStartRejectedException("DATA_SYNC rejected before engine submission", failure);
        }
    }
}
