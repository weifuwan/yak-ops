package io.yak.ops.worker.task.executor;

import io.yak.ops.worker.task.executor.container.ITaskExecutorContainer;
import io.yak.ops.worker.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.worker.task.executor.eventbus.ITaskExecutorEventBusCoordinator;
import io.yak.ops.worker.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.worker.task.executor.events.TaskExecutorKillLifecycleEvent;
import java.util.Collection;

/** DolphinScheduler TaskEngine, with Yak Ops duplicate-ID and dispatch rollback safeguards. */
public class TaskEngine implements ITaskEngine {
    private final String engineName;
    private final ITaskExecutorRepository taskExecutorRepository;
    private final ITaskExecutorContainerProvider<?> taskExecutorContainerDelegator;
    private final ITaskExecutorEventBusCoordinator taskExecutorEventBusCoordinator;

    public TaskEngine(TaskEngineBuilder builder) {
        engineName = builder.getEngineName();
        taskExecutorRepository = java.util.Objects.requireNonNull(builder.getTaskExecutorRepository());
        taskExecutorContainerDelegator = java.util.Objects.requireNonNull(builder.getTaskExecutorContainerDelegator());
        taskExecutorEventBusCoordinator = java.util.Objects.requireNonNull(builder.getTaskExecutorEventBusCoordinator());
    }

    @Override public void start() { taskExecutorEventBusCoordinator.start(); }

    @Override
    public synchronized void submitTask(ITaskExecutor executor) {
        if (taskExecutorRepository.contains(executor.getId())) {
            throw new IllegalStateException("Task executor already submitted: " + executor.getId());
        }
        ITaskExecutorContainer container = taskExecutorContainerDelegator.getExecutorContainer();
        boolean dispatched = false;
        boolean registered = false;
        try {
            container.dispatch(executor);
            dispatched = true;
            taskExecutorRepository.put(executor);
            registered = true;
            executor.getTaskExecutorEventBus().publish(TaskExecutorDispatchedLifecycleEvent.of(executor));
            container.start(executor);
        } catch (RuntimeException failure) {
            if (registered) taskExecutorRepository.remove(executor.getId());
            if (dispatched) container.finalize(executor);
            throw failure;
        }
    }

    @Override public void pauseTask(String id) {
        throw new UnsupportedOperationException("Pause is not supported by the embedded Task SPI");
    }

    @Override public void killTask(String id) {
        ITaskExecutor executor = getTaskExecutor(id);
        executor.getTaskExecutorEventBus().publish(TaskExecutorKillLifecycleEvent.of(executor));
    }

    @Override public Collection<ITaskExecutor> queryTaskExecutors() {
        return taskExecutorRepository.getAll();
    }

    private ITaskExecutor getTaskExecutor(String id) {
        return taskExecutorRepository.get(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Task Executor: " + id));
    }

    @Override public void close() {
        taskExecutorEventBusCoordinator.close();
        taskExecutorContainerDelegator.getExecutorContainer().close();
    }
}
