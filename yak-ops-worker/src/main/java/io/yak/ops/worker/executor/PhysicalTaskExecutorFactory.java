package io.yak.ops.worker.executor;

import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Creates one physical task per admitted Task Instance, without binding to a particular plugin type. */
public final class PhysicalTaskExecutorFactory {
    public PhysicalTaskExecutor createTaskExecutor(
            String workspaceId, String instanceId, Supplier<CompletionStage<TaskOutcome>> task, Runnable cancellation) {
        return new PhysicalTaskExecutor(new TaskExecutionContext(workspaceId, instanceId), task, cancellation);
    }
}
