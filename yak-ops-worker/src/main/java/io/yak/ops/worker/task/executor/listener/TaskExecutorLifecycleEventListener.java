package io.yak.ops.worker.task.executor.listener;

import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.ITaskExecutorRepository;
import io.yak.ops.worker.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.worker.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.worker.task.executor.events.IReportableTaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.events.ITaskExecutorLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorFinalizeLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorKillLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorPauseLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorSuccessLifecycleEvent;

public class TaskExecutorLifecycleEventListener implements ITaskExecutorLifecycleEventListener {
    private final ITaskExecutorContainerProvider<?> taskExecutorContainerDelegator;
    private final ITaskExecutorRepository taskExecutorRepository;
    private final ITaskExecutorLifecycleEventReporter taskExecutorLifecycleEventReporter;

    public TaskExecutorLifecycleEventListener(
            ITaskExecutorContainerProvider<?> container,
            ITaskExecutorRepository repository,
            ITaskExecutorLifecycleEventReporter reporter) {
        this.taskExecutorContainerDelegator = container;
        this.taskExecutorRepository = repository;
        this.taskExecutorLifecycleEventReporter = reporter;
    }

    @Override
    public void onTaskExecutorDispatchedLifecycleEvent(TaskExecutorDispatchedLifecycleEvent event) {
        report(event);
    }

    @Override
    public void onTaskExecutorStartedLifecycleEvent(TaskExecutorStartedLifecycleEvent event) {
        report(event);
    }

    @Override
    public void onTaskExecutorRuntimeContextChangedEvent(TaskExecutorRuntimeContextChangedLifecycleEvent event) {
        report(event);
    }

    @Override
    public void onTaskExecutorPauseLifecycleEvent(TaskExecutorPauseLifecycleEvent event) {
        getTaskExecutor(event).pause();
    }

    @Override
    public void onTaskExecutorPausedLifecycleEvent(TaskExecutorPausedLifecycleEvent event) {
        report(event);
    }

    @Override
    public void onTaskExecutorKillLifecycleEvent(TaskExecutorKillLifecycleEvent event) {
        getTaskExecutor(event).kill();
    }

    @Override
    public void onTaskExecutorKilledLifecycleEvent(TaskExecutorKilledLifecycleEvent event) {
        report(event);
    }

    @Override
    public void onTaskExecutorSuccessLifecycleEvent(TaskExecutorSuccessLifecycleEvent event) {
        report(event);
    }

    @Override
    public void onTaskExecutorFailLifecycleEvent(TaskExecutorFailedLifecycleEvent event) {
        report(event);
    }

    @Override
    public void onTaskExecutorFinalizeLifecycleEvent(TaskExecutorFinalizeLifecycleEvent event) {
        ITaskExecutor executor = getTaskExecutor(event);
        // The local reporter generates FINALIZE only after the TaskHandle outcome was persisted.
        taskExecutorContainerDelegator.getExecutorContainer().finalize(executor);
        taskExecutorRepository.remove(executor.getId());
    }

    private void report(IReportableTaskExecutorLifecycleEvent event) {
        taskExecutorLifecycleEventReporter.reportTaskExecutorLifecycleEvent(event);
    }

    private ITaskExecutor getTaskExecutor(ITaskExecutorLifecycleEvent event) {
        return taskExecutorRepository
                .get(event.getTaskInstanceId())
                .orElseThrow(() -> new IllegalStateException("Unknown TaskExecutor: " + event.getTaskInstanceId()));
    }
}
