package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorRepository;
import io.yak.ops.worker.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.worker.task.executor.events.IReportableTaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorFinalizeLifecycleEvent;
import java.util.Objects;

/**
 * Local adaptation of DolphinScheduler's ACK-driven Reporter. Physical Task completion
 * is delivered only after the Yak Ops Observer has committed its business outcome.
 */
public final class LocalTaskExecutorLifecycleEventReporter implements ITaskExecutorLifecycleEventReporter {
    private final ITaskExecutorRepository repository;

    public LocalTaskExecutorLifecycleEventReporter(ITaskExecutorRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public void start() {}

    @Override
    public void reportTaskExecutorLifecycleEvent(IReportableTaskExecutorLifecycleEvent event) {
        if (!event.getType().isFinished()) return;
        ITaskExecutor executor = repository
                .get(event.getTaskInstanceId())
                .orElseThrow(() ->
                        new IllegalStateException("Cannot acknowledge missing task: " + event.getTaskInstanceId()));
        if (!(executor instanceof PhysicalTaskExecutor physicalTask) || !physicalTask.isPersistedTerminal()) {
            throw new IllegalStateException("Task cannot be finalized before durable terminal observation");
        }
        receiveTaskExecutorLifecycleEventACK(new TaskExecutorLifecycleEventAck(executor.getId(), event.getType()));
    }

    @Override
    public void receiveTaskExecutorLifecycleEventACK(TaskExecutorLifecycleEventAck ack) {
        repository
                .get(ack.getTaskExecutorId())
                .ifPresent(executor ->
                        executor.getTaskExecutorEventBus().publish(TaskExecutorFinalizeLifecycleEvent.of(executor)));
    }

    @Override
    public void onWorkflowInstanceHostChanged(String taskInstanceId) {
        // No remote Master in embedded mode.
    }

    @Override
    public void close() {}
}
