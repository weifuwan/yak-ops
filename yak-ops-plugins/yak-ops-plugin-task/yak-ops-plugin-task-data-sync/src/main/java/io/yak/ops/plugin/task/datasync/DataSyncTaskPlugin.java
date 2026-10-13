package io.yak.ops.plugin.task.datasync;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.execution.DataSyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.validation.JobConfigValidator;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Owns DATA_SYNC Task definition rules and delegates physical planning and execution. */
public final class DataSyncTaskPlugin implements ExecutableTaskPlugin {

    public static final String TYPE = "DATA_SYNC";

    private final JobPlanner planner;
    private final DataSyncTaskExecutor executor;
    private final FactoryUtil factories;

    public DataSyncTaskPlugin(JobPlanner planner, DataSyncTaskExecutor executor, FactoryUtil factories) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int parameterSchemaVersion() {
        return 2;
    }

    @Override
    public OptionRule optionRule() {
        return JobOptions.PIPELINE_RULE;
    }

    @Override
    public ReadonlyConfig parse(String content) {
        try {
            ReadonlyConfig parsed = ConfigParser.parseJson(content);
            return JobConfigValidator.validateConfig(parsed, factories).parameters();
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC pipeline JSON", failure);
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
    public TaskAdmissionPlan plan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
        return planner.admissionPlan(parameters, context);
    }

    @Override
    public CompletionStage<TaskHandle> start(
            ReadonlyConfig parameters, TaskAdmissionPlan plan, TaskExecutionIdentity context, boolean debug) {
        return executor.start(parameters, plan, context, debug);
    }
}
