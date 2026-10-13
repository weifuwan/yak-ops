package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.execution.SyncExecutionPlanner;
import io.yak.ops.plugin.task.datasync.execution.SyncTaskExecutor;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Defines DATA_SYNC parameters and delegates planning and physical execution to dedicated components. */
public final class SyncTaskPlugin implements ExecutableTaskPlugin {

    public static final String TYPE = "DATA_SYNC";

    private final SyncExecutionPlanner planner;
    private final SyncTaskExecutor executor;

    public SyncTaskPlugin(SyncExecutionPlanner planner, SyncTaskExecutor executor) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
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
    public TaskParameters parse(String content) {
        SyncParameters parameters;
        try {
            parameters = JSONUtils.parseObject(content, SyncParameters.class);
        } catch (IllegalArgumentException exception) {
            throw new TaskPluginException("Invalid DATA_SYNC pipeline JSON");
        }
        SyncValidator.checkParameters(parameters);
        return parameters;
    }

    @Override
    public void validate(TaskParameters parameters, TaskValidationContext context) {
        if (!(parameters instanceof SyncParameters pipeline)) {
            throw new TaskPluginException("Invalid DATA_SYNC parameter type");
        }
        SyncValidator.checkResources(pipeline, context);
    }

    @Override
    public TaskExecutionPlan plan(TaskParameters parameters, TaskExecutionContext context) {
        if (!(parameters instanceof SyncParameters pipeline)) {
            throw new TaskPluginException("Invalid DATA_SYNC parameter type");
        }
        return planner.plan(pipeline, context);
    }

    @Override
    public CompletionStage<TaskHandle> start(
            TaskParameters parameters, TaskExecutionPlan plan, TaskExecutionContext context) {
        return executor.start(parameters, plan, context);
    }
}
