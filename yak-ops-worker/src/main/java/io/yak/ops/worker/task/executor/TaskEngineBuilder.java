package io.yak.ops.worker.task.executor;

import io.yak.ops.worker.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.worker.task.executor.eventbus.ITaskExecutorEventBusCoordinator;

/** DolphinScheduler TaskEngineBuilder adapted to Yak Ops embedded worker configuration. */
public final class TaskEngineBuilder {
    private String engineName;
    private ITaskExecutorRepository taskExecutorRepository;
    private ITaskExecutorContainerProvider<?> taskExecutorContainerDelegator;
    private ITaskExecutorEventBusCoordinator taskExecutorEventBusCoordinator;

    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(String value) {
        engineName = value;
    }

    public ITaskExecutorRepository getTaskExecutorRepository() {
        return taskExecutorRepository;
    }

    public void setTaskExecutorRepository(ITaskExecutorRepository value) {
        taskExecutorRepository = value;
    }

    public ITaskExecutorContainerProvider<?> getTaskExecutorContainerDelegator() {
        return taskExecutorContainerDelegator;
    }

    public void setTaskExecutorContainerDelegator(ITaskExecutorContainerProvider<?> value) {
        taskExecutorContainerDelegator = value;
    }

    public ITaskExecutorEventBusCoordinator getTaskExecutorEventBusCoordinator() {
        return taskExecutorEventBusCoordinator;
    }

    public void setTaskExecutorEventBusCoordinator(ITaskExecutorEventBusCoordinator value) {
        taskExecutorEventBusCoordinator = value;
    }
}
