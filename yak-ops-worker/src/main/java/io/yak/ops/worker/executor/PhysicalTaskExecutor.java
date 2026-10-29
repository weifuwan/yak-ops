package io.yak.ops.worker.executor;

import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.worker.task.executor.AbstractTaskExecutor;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import io.yak.ops.worker.task.executor.TaskExecutorState;
import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBus;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * DolphinScheduler PhysicalTaskExecutor adapted to Yak Ops's asynchronous TaskHandle.
 * A task occupies its exclusive slot until the observer has persisted its outcome.
 */
public final class PhysicalTaskExecutor extends AbstractTaskExecutor {
    private final Supplier<CompletionStage<TaskOutcome>> submission;
    private final Runnable cancellation;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();

    public PhysicalTaskExecutor(
            TaskExecutionContext context, Supplier<CompletionStage<TaskOutcome>> submission, Runnable cancellation) {
        super(context, new TaskExecutorEventBus());
        this.submission = Objects.requireNonNull(submission);
        this.cancellation = Objects.requireNonNull(cancellation);
    }

    @Override
    protected void initializeTaskPlugin() {
        // The submitted action resolves the pinned Task definition and existing Task SPI.
    }

    @Override
    protected void doTriggerTaskPlugin() {
        try {
            CompletionStage<TaskOutcome> completion = submission.get();
            if (completion == null) throw new IllegalStateException("Task submission returned null completion");
            completion.whenComplete((outcome, error) -> {
                if (error != null || outcome == null) {
                    transitTaskExecutorState(TaskExecutorState.FAILED);
                    return;
                }
                switch (outcome) {
                    case FINISHED:
                        transitTaskExecutorState(TaskExecutorState.SUCCEEDED);
                        break;
                    case CANCELED:
                        transitTaskExecutorState(TaskExecutorState.KILLED);
                        break;
                    case FAILED:
                        transitTaskExecutorState(TaskExecutorState.FAILED);
                        break;
                    default:
                        transitTaskExecutorState(TaskExecutorState.FAILED);
                }
            });
        } catch (RuntimeException failure) {
            transitTaskExecutorState(TaskExecutorState.FAILED);
            throw failure;
        }
    }

    @Override
    protected TaskExecutorState doTrackTaskPluginStatus() {
        return taskExecutorState.get();
    }

    @Override
    public void pause() {
        throw new UnsupportedOperationException("Pause is not supported by the embedded Task SPI");
    }

    @Override
    public void kill() {
        if (cancellationRequested.compareAndSet(false, true)) cancellation.run();
    }

    @Override
    public void finalizeTask() {
        // Physical resources are closed by TaskHandle and its completion observer.
    }
}
