package io.yak.ops.sync.runtime;

import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.composer.PipelineComposer;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.sync.runtime.config.PipelineExecutionPlan;
import io.yak.ops.sync.runtime.validation.PipelineDefinitionValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses Flink CDC Pipeline YAML and coordinates YakFlow planning, admission, and execution.
 *
 * <p>The Task Plugin owns transport conversion. This service receives YAML only; it does not
 * understand the frontend JSON contract.
 */
public final class PipelineService {
    private final FactoryUtil factories;
    private final PipelineComposer composer;
    private final YamlPipelineDefinitionParser parser = new YamlPipelineDefinitionParser();

    public PipelineService(FactoryUtil factories, PipelineComposer composer) {
        this.factories = Objects.requireNonNull(factories, "factories");
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    /** Parses and validates a Pipeline definition and returns server-derived query indexes. */
    public io.yak.ops.configuration.ReadonlyConfig parse(String yaml) {
        try {
            PipelineDef definition = parser.parse(yaml);
            PipelineExecutionPlan plan = PipelineDefinitionValidator.validateConfig(definition, factories);
            Map<String, Object> index = Map.of(
                    "sourceType", plan.solutionType().sourceIdentifier(),
                    "solutionType", plan.solutionType().name(),
                    "sourceDataSourceId", plan.sourceDataSourceId(),
                    "targetDataSourceId", plan.targetDataSourceId());
            return io.yak.ops.configuration.ReadonlyConfig.fromMap(Map.of("index", index));
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC Pipeline YAML", failure);
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

    public void validateUpdate(String previousYaml, String updatedYaml, TaskValidationContext context) {
        PipelineExecutionPlan before = PipelineExecutionPlan.from(parser.parse(previousYaml));
        PipelineExecutionPlan after = PipelineExecutionPlan.from(parser.parse(updatedYaml));
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

    public void validateReferences(String yaml, TaskValidationContext context) {
        PipelineDefinitionValidator.validateReferences(parser.parse(yaml), context, factories);
    }

    public void validate(String yaml, TaskValidationContext context) {
        PipelineDefinitionValidator.validateResources(parser.parse(yaml), context, factories);
    }

    public boolean supportsScheduling(String yaml) {
        String identifier =
                PipelineExecutionPlan.from(parser.parse(yaml)).solutionType().sourceIdentifier();
        return factories.source(identifier).boundedness() == Boundedness.BOUNDED;
    }

    public TaskExecutionResources plan(String yaml, TaskExecutionIdentity identity) {
        PipelineDef definition = parser.parse(yaml);
        return admission(composer.plan(definition, context(identity, false)));
    }

    public PipelineExecution submit(
            String yaml, TaskExecutionResources plan, TaskExecutionIdentity identity, boolean debug) {
        try {
            PipelineContext trusted = context(identity, debug);
            PipelineDef definition = parser.parse(yaml);
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
