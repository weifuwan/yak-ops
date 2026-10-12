package io.yak.ops.plugin.task.api.execution;

import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPlugin;
import java.util.concurrent.CompletionStage;

/**
 * Optional execution capability of one registered TaskPlugin.
 *
 * <p>Planning is read-only. A trusted platform obtains durable admission and a
 * single-dispatch claim before calling start. Implementations do not persist
 * business Instances, Attempts, or resource claims.
 */
public interface ExecutableTaskPlugin extends TaskPlugin {

    /**
     * Declares canonical exclusive resources and a non-secret binding revision.
     *
     * @param parameters validated, immutable Task configuration
     * @param context authorized execution identity and pinned Definition version
     * @return a read-only execution plan to be admitted by the platform
     */
    TaskExecutionPlan plan(TaskParameters parameters, TaskExecutionContext context);

    /**
     * Starts physical execution from an admitted plan.
     *
     * <p>Implementations must validate the persisted resource binding before any
     * external execution effect, and fail without launching on a mismatch.
     *
     * @param parameters validated Task configuration pinned to the Definition version
     * @param plan resource plan whose claims have been durably admitted
     * @param context identity of the authorized Instance and Attempt
     * @return an asynchronous handle to the physical execution
     */
    CompletionStage<TaskHandle> start(TaskParameters parameters, TaskExecutionPlan plan, TaskExecutionContext context);
}
