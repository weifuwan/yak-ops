package io.yak.ops.plugin.task.datasync;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Thin DolphinScheduler-style DATA_SYNC TaskChannel. Engine validation and planning stay in PipelineService. */
public final class PipelineTaskChannel implements TaskChannel {
    public static final String TYPE = "DATA_SYNC";

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
        return 5;
    }

    @Override
    public OptionRule optionRule() {
        return service.optionRule();
    }

    public PipelineParameters parseParameters(String content) {
        PipelineParameters parameters = new PipelineParameters(content);
        if (!parameters.checkParameters()) {
            throw new TaskPluginException("DATA_SYNC requires V5 Pipeline JSON");
        }
        return parameters;
    }

    @Override
    public ReadonlyConfig parse(String content) {
        return service.parse(parseParameters(content).getPipelineJson());
    }

    @Override
    public void validateUpdate(ReadonlyConfig previous, ReadonlyConfig updated, TaskValidationContext context) {
        service.validateUpdate(previous, updated, context);
    }

    @Override
    public void validateReferences(ReadonlyConfig parameters, TaskValidationContext context) {
        service.validateReferences(parameters, context);
    }

    @Override
    public void validate(ReadonlyConfig parameters, TaskValidationContext context) {
        service.validate(parameters, context);
    }

    @Override
    public boolean supportsScheduling(ReadonlyConfig parameters) {
        return service.supportsScheduling(parameters);
    }

    @Override
    public TaskExecutionResources plan(ReadonlyConfig parameters, TaskExecutionIdentity identity) {
        return service.plan(parameters, identity);
    }

    @Override
    public CompletionStage<TaskHandle> createTask(
            ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity identity, boolean debug) {
        try {
            return CompletableFuture.completedFuture(new PipelineTask(service.submit(parameters, plan, identity, debug)));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
