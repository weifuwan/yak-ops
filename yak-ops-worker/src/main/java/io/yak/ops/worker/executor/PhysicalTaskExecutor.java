package io.yak.ops.worker.executor;

import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.worker.task.executor.AbstractTaskExecutor;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import io.yak.ops.worker.task.executor.TaskExecutorState;
import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBus;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DolphinScheduler PhysicalTaskExecutor adapted to the existing async TaskHandle.
 * Initialization resolves the physical plugin; handle triggers its execution.
 */
public final class PhysicalTaskExecutor extends AbstractTaskExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(PhysicalTaskExecutor.class);
    private final PhysicalTaskPluginFactory physicalTaskPluginFactory;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private volatile PhysicalTask physicalTask;
    private volatile boolean persistedTerminal;
    private volatile boolean uncertainOutcome;

    public PhysicalTaskExecutor(TaskExecutionContext context, PhysicalTaskPluginFactory factory) {
        super(context, new TaskExecutorEventBus());
        physicalTaskPluginFactory = java.util.Objects.requireNonNull(factory);
    }

    @Override
    protected void initializeTaskPlugin() {
        try {
            physicalTask = java.util.Objects.requireNonNull(
                    physicalTaskPluginFactory.createPhysicalTask(taskExecutionContext),
                    "PhysicalTaskPluginFactory returned null");
        } catch (RuntimeException failure) {
            uncertainOutcome = true;
            LOG.error("Physical plugin initialization could not be confirmed, instanceId={}", getId(), failure);
            physicalTask = new PhysicalTask() {
                @Override public CompletionStage<TaskOutcome> handle() {
                    return java.util.concurrent.CompletableFuture.failedFuture(failure);
                }
                @Override public void cancel() { }
            };
        }
        if (cancellationRequested.get()) physicalTask.cancel();
    }

    @Override
    protected void doTriggerTaskPlugin() {
        try {
            CompletionStage<TaskOutcome> completion = physicalTask.handle();
            if (completion == null) throw new IllegalStateException("PhysicalTask.handle returned null");
            completion.whenComplete((outcome, failure) -> {
                if (failure != null || outcome == null) {
                    uncertainOutcome = true;
                    LOG.error("Physical Task outcome uncertain, instanceId={}", getId(), failure);
                    return;
                }
                persistedTerminal = true;
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
                        uncertainOutcome = true;
                        persistedTerminal = false;
                }
            });
        } catch (RuntimeException failure) {
            uncertainOutcome = true;
            LOG.error("Physical Task submission state unknown, instanceId={}", getId(), failure);
        }
    }

    @Override protected TaskExecutorState doTrackTaskPluginStatus() { return taskExecutorState.get(); }
    public boolean isPersistedTerminal() { return persistedTerminal; }
    public boolean isUncertainOutcome() { return uncertainOutcome; }

    @Override public void pause() { throw new UnsupportedOperationException("Task pause is not supported"); }

    @Override public void kill() {
        if (cancellationRequested.compareAndSet(false, true)) {
            PhysicalTask task = physicalTask;
            if (task != null) task.cancel();
        }
    }

    @Override public void finalizeTask() {
        // TaskHandle closes its own physical resources on completion.
    }
}
