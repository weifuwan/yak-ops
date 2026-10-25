package io.yak.ops.plugin.task.datasync;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.config.SolutionOptions;
import io.yak.ops.plugin.task.datasync.execution.DataSyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.solution.Solution;
import io.yak.ops.plugin.task.datasync.solution.SolutionCatalog;
import io.yak.ops.plugin.task.datasync.validation.JobConfigValidator;
import java.util.List;
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
        return 4;
    }

    @Override
    public OptionRule optionRule() {
        return SolutionOptions.RULE;
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

    /**
     * Plans target DDL from authorized metadata without executing statements or creating a Task.
     *
     * @param parameters requested version-four product parameters
     * @param workspaceId trusted Workspace identity
     * @return ordered mapping previews with no connection credentials
     */
    public List<io.yak.ops.plugin.task.datasync.plan.SchemaPreview> preview(String parameters, String workspaceId) {
        return planner.preview(parse(parameters), workspaceId);
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
    public TaskAdmissionPlan plan(ReadonlyConfig parameters, TaskExecutionIdentity context) {
        return planner.admissionPlan(parameters, context);
    }

    @Override
    public CompletionStage<TaskHandle> start(
            ReadonlyConfig parameters, TaskAdmissionPlan plan, TaskExecutionIdentity context, boolean debug) {
        return executor.start(parameters, plan, context, debug);
    }
}
