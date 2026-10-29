package io.yak.ops.plugin.task.api.execution;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskPlugin;
import java.util.concurrent.CompletionStage;

/** Execution capability of a registered Task Plugin. */
public interface ExecutableTaskPlugin extends TaskPlugin {

    /**
     * Declares whether these parsed parameters permit periodic submission without external access.
     * @param parameters validated immutable plugin parameters
     * @return true for configurations suitable for periodic execution; false by default
     */
    default boolean supportsScheduling(ReadonlyConfig parameters) {
        return false;
    }

    /**
     * Resolves exclusive execution resources and their metadata revision before submission.
     *
     * @param parameters validated immutable Task configuration
     * @param context identity of the selected immutable Definition version
     * @return resources checked at Task Instance submission and Worker startup
     */
    TaskExecutionResources plan(ReadonlyConfig parameters, TaskExecutionIdentity context);

    /**
     * Starts physical execution after durable Instance submission and resource revalidation.
     *
     * @param parameters pinned Task configuration
     * @param plan resource claims and binding revision of the submitted Task Instance
     * @param context authorized Instance and Attempt identity
     * @param debug detailed diagnostics captured for this Instance
     * @return execution handle
     */
    CompletionStage<TaskHandle> start(
            ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity context, boolean debug);
}
