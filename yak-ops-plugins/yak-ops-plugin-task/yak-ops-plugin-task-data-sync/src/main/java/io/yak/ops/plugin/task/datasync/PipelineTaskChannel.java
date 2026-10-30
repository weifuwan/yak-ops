package io.yak.ops.plugin.task.datasync;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.sync.runtime.PipelineService;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Thin DolphinScheduler-style DATA_SYNC TaskChannel. Engine validation and planning stay in PipelineService. */
public final class PipelineTaskChannel implements TaskChannel {
    public static final String TYPE = "DATA_SYNC";

    private static final Option<String> PIPELINE_YAML = Options.key("pipelineYaml").stringType().noDefaultValue();
    private static final Option<Map<String, Object>> INDEX = Options.key("index").mapObjectType().noDefaultValue();
    private static final OptionRule RULE = OptionRule.builder().required(PIPELINE_YAML).optional(INDEX).build();

    private final PipelineService service;

    public PipelineTaskChannel(PipelineService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int parameterSchemaVersion() {
        return 6;
    }

    @Override
    public OptionRule optionRule() {
        return RULE;
    }

    public PipelineParameters parseParameters(String content) {
        ReadonlyConfig envelope = ConfigParser.parseJson(content);
        Object yaml = envelope.getSourceMap().get("pipelineYaml");
        PipelineParameters parameters = new PipelineParameters(yaml instanceof String text ? text : null);
        if (!parameters.checkParameters()) {
            throw new TaskPluginException("DATA_SYNC requires Pipeline YAML");
        }
        return parameters;
    }

    @Override
    public ReadonlyConfig parse(String content) {
        PipelineParameters parameters = parseParameters(content);
        ReadonlyConfig metadata = service.parse(parameters.getPipelineYaml());
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("pipelineYaml", parameters.getPipelineYaml());
        stored.putAll(metadata.getSourceMap());
        return ReadonlyConfig.fromMap(stored);
    }

    @Override
    public void validateUpdate(ReadonlyConfig previous, ReadonlyConfig updated, TaskValidationContext context) {
        service.validateUpdate(PipelineParameters.yaml(previous), PipelineParameters.yaml(updated), context);
    }

    @Override
    public void validateReferences(ReadonlyConfig parameters, TaskValidationContext context) {
        service.validateReferences(PipelineParameters.yaml(parameters), context);
    }

    @Override
    public void validate(ReadonlyConfig parameters, TaskValidationContext context) {
        service.validate(PipelineParameters.yaml(parameters), context);
    }

    @Override
    public boolean supportsScheduling(ReadonlyConfig parameters) {
        return service.supportsScheduling(PipelineParameters.yaml(parameters));
    }

    @Override
    public TaskExecutionResources plan(ReadonlyConfig parameters, TaskExecutionIdentity identity) {
        return service.plan(PipelineParameters.yaml(parameters), identity);
    }

    @Override
    public CompletionStage<TaskHandle> createTask(
            ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity identity, boolean debug) {
        try {
            return CompletableFuture.completedFuture(
                    new PipelineTask(service.submit(PipelineParameters.yaml(parameters), plan, identity, debug)));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
