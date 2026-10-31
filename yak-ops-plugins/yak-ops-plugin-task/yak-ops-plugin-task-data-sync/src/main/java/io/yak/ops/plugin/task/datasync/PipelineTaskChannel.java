package io.yak.ops.plugin.task.datasync;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.PipelineComposer;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.composer.validation.PipelineDefinitionValidator;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * DATA_SYNC Task boundary for JSON conversion, authorization, and deferred Pipeline execution.
 *
 * <p>Only JSON and a server-generated search index are persisted. The YAML representation is
 * produced at the Task boundary for each validation, plan, and execution invocation.
 */
public final class PipelineTaskChannel implements TaskChannel {
    public static final String TYPE = "DATA_SYNC";
    private static final Option<Map<String, Object>> SOURCE =
            Options.key("source").mapObjectType().noDefaultValue();
    private static final Option<Map<String, Object>> SINK =
            Options.key("sink").mapObjectType().noDefaultValue();
    private static final Option<List<Object>> ROUTE =
            Options.key("route").listType(Object.class).noDefaultValue();
    private static final Option<List<Object>> TRANSFORM =
            Options.key("transform").listType(Object.class).noDefaultValue();
    private static final Option<Map<String, Object>> PIPELINE =
            Options.key("pipeline").mapObjectType().noDefaultValue();
    private static final Option<Map<String, Object>> INDEX =
            Options.key("index").mapObjectType().noDefaultValue();
    private static final OptionRule RULE = OptionRule.builder()
            .required(SOURCE, SINK, ROUTE, PIPELINE)
            .optional(TRANSFORM, INDEX)
            .build();

    private final FactoryUtil factories;
    private final PipelineComposer composer;
    private final YamlPipelineDefinitionParser parser = new YamlPipelineDefinitionParser();

    public PipelineTaskChannel(FactoryUtil factories, PipelineComposer composer) {
        this.factories = Objects.requireNonNull(factories, "factories");
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int parameterSchemaVersion() {
        return 8;
    }

    @Override
    public OptionRule optionRule() {
        return RULE;
    }

    public PipelineParameters parseParameters(String content) {
        return new PipelineParameters(ConfigParser.parseJson(content));
    }

    @Override
    public ReadonlyConfig parse(String content) {
        return parseRequest(parseParameters(content).getJson());
    }

    /**
     * Reads immutable published schema 7 definitions without rewriting their stored payload.
     * New writes use schema 8; both versions share the same validated execution path.
     *
     * @param schemaVersion version recorded on the immutable definition
     * @param content original parameter JSON
     * @return validated parameters with a derived search index
     */
    @Override
    public ReadonlyConfig parse(int schemaVersion, String content) {
        if (schemaVersion != 7 && schemaVersion != parameterSchemaVersion()) {
            throw new TaskPluginException("Unsupported DATA_SYNC parameter schema version");
        }
        ReadonlyConfig request = parseParameters(content).getJson();
        if (schemaVersion == 7) PipelineParameterVersions.validateSeven(request.getSourceMap());
        return parseRequest(request);
    }

    private ReadonlyConfig parseRequest(ReadonlyConfig request) {
        String yaml = PipelineYamlConverter.convert(request);
        try {
            PipelineOptions options = PipelineDefinitionValidator.validateConfig(parser.parse(yaml), factories);
            Map<String, Object> stored = new LinkedHashMap<>(request.getSourceMap());
            // Never trust client-provided index values; derive from the validated Pipeline.
            stored.put(
                    "index",
                    Map.of(
                            "sourceType", options.sourceIdentifier(),
                            "sinkType", options.sinkIdentifier(),
                            "runtimeMode", options.runtimeMode(),
                            "tableScope", options.tableScope(),
                            "sourceDataSourceId", options.sourceDataSourceId(),
                            "targetDataSourceId", options.targetDataSourceId()));
            return ReadonlyConfig.fromMap(stored);
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC Pipeline definition", failure);
        }
    }

    @Override
    public void validateUpdate(ReadonlyConfig previous, ReadonlyConfig updated, TaskValidationContext context) {
        PipelineOptions before = PipelineDefinitionValidator.validateConfig(definition(previous), factories);
        PipelineOptions after = PipelineDefinitionValidator.validateConfig(definition(updated), factories);
        if (!before.sourceIdentifier().equals(after.sourceIdentifier())
                || !before.sinkIdentifier().equals(after.sinkIdentifier())
                || !context.dataSource(before.sourceDataSourceId())
                        .type()
                        .equals(context.dataSource(after.sourceDataSourceId()).type())
                || !context.dataSource(before.targetDataSourceId())
                        .type()
                        .equals(context.dataSource(after.targetDataSourceId()).type())) {
            throw new TaskPluginException("Changing connectors or datasource types requires a new task");
        }
    }

    @Override
    public void validateReferences(ReadonlyConfig parameters, TaskValidationContext context) {
        PipelineDefinitionValidator.validateReferences(definition(parameters), context, factories);
    }

    @Override
    public void validate(ReadonlyConfig parameters, TaskValidationContext context) {
        PipelineDefinitionValidator.validateResources(definition(parameters), context, factories);
    }

    @Override
    public boolean supportsScheduling(ReadonlyConfig parameters) {
        return "BATCH"
                .equals(PipelineDefinitionValidator.validateConfig(definition(parameters), factories)
                        .runtimeMode());
    }

    @Override
    public TaskExecutionResources plan(ReadonlyConfig parameters, TaskExecutionIdentity identity) {
        return admission(composer.plan(definition(parameters), context(identity, false)));
    }

    @Override
    public CompletionStage<TaskHandle> createTask(
            ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity identity, boolean debug) {
        try {
            PipelineContext trusted = context(identity, debug);
            PipelineDef definition = definition(parameters);
            PipelinePlan admitted = composer.plan(definition, trusted);
            if (!plan.equals(admission(admitted))) {
                throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
            }
            return CompletableFuture.completedFuture(new PipelineTask(composer.compose(definition, admitted, trusted)));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(
                    new TaskStartRejectedException("DATA_SYNC rejected before engine submission", failure));
        }
    }

    private PipelineDef definition(ReadonlyConfig json) {
        return parser.parse(PipelineYamlConverter.convert(json));
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
}
