package io.yak.ops.plugin.task.datasync;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.sync.runtime.PipelineService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Thin DATA_SYNC Task adapter: API JSON -> V6 YAML -> engine.
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
        return new PipelineParameters(ConfigParser.parseJson(content));
    }

    @Override
    public ReadonlyConfig parse(String content) {
        ReadonlyConfig request = parseParameters(content).getJson();
        String yaml = PipelineYamlConverter.convert(request);
        ReadonlyConfig derived = service.parse(yaml);
        Map<String, Object> stored = new LinkedHashMap<>(request.getSourceMap());
        // Never trust client-provided index values; derive from the validated YAML.
        stored.remove("index");
        stored.putAll(derived.getSourceMap());
        return ReadonlyConfig.fromMap(stored);
    }

    @Override
    public void validateUpdate(ReadonlyConfig previous, ReadonlyConfig updated, TaskValidationContext context) {
        service.validateUpdate(yaml(previous), yaml(updated), context);
    }

    @Override
    public void validateReferences(ReadonlyConfig parameters, TaskValidationContext context) {
        service.validateReferences(yaml(parameters), context);
    }

    @Override
    public void validate(ReadonlyConfig parameters, TaskValidationContext context) {
        service.validate(yaml(parameters), context);
    }

    @Override
    public boolean supportsScheduling(ReadonlyConfig parameters) {
        return service.supportsScheduling(yaml(parameters));
    }

    @Override
    public TaskExecutionResources plan(ReadonlyConfig parameters, TaskExecutionIdentity identity) {
        return service.plan(yaml(parameters), identity);
    }

    @Override
    public CompletionStage<TaskHandle> createTask(
            ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity identity, boolean debug) {
        try {
            return CompletableFuture.completedFuture(
                    new PipelineTask(service.submit(yaml(parameters), plan, identity, debug)));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static String yaml(ReadonlyConfig json) {
        return PipelineYamlConverter.convert(json);
    }
}
