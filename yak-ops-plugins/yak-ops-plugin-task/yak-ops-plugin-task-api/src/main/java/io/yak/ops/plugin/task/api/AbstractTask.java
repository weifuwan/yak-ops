package io.yak.ops.plugin.task.api;

import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import java.util.concurrent.CompletionStage;

/**
 * DolphinScheduler-style physical Task contract.
 *
 * <p>Yak Ops uses an asynchronous terminal outcome, because an embedded YakFlow job can
 * continue running after its physical submission. The Worker owns its lifecycle.
 */
public abstract class AbstractTask {
    /** Triggers the task and completes only when its physical execution reaches a terminal outcome. */
    public abstract CompletionStage<TaskOutcome> handle();

    /** Requests cancellation; a request alone is not a terminal outcome. */
    public abstract void cancel();
}
