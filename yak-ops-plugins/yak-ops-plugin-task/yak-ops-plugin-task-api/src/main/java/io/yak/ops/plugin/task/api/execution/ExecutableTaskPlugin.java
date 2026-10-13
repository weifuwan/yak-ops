package io.yak.ops.plugin.task.api.execution;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskPlugin;
import java.util.concurrent.CompletionStage;

/** Execution capability of a registered Task Plugin. */
public interface ExecutableTaskPlugin extends TaskPlugin {

    /**
     * Declares canonical exclusive resources and resource-binding revision.
     *
     * @param parameters validated immutable Task configuration
     * @param context identity of the selected immutable Definition version
     * @return read-only admission plan
     */
    TaskAdmissionPlan plan(ReadonlyConfig parameters, TaskExecutionIdentity context);

    /**
     * Starts physical execution after durable admission and resource revalidation.
     *
     * @param parameters pinned Task configuration
     * @param plan admitted resource claims and binding revision
     * @param context authorized Instance and Attempt identity
     * @param debug detailed diagnostics captured for this Instance
     * @return execution handle
     */
    CompletionStage<TaskHandle> start(
            ReadonlyConfig parameters, TaskAdmissionPlan plan, TaskExecutionIdentity context, boolean debug);
}
