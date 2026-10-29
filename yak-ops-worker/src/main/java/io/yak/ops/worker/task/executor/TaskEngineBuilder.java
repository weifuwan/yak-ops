package io.yak.ops.worker.task.executor;

import io.yak.ops.worker.task.executor.container.ITaskExecutorContainerProvider;
import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBusCoordinator;

/** DolphinScheduler TaskEngineBuilder fields, adapted to local lifecycle coordination. */
public final class TaskEngineBuilder {
    private String engineName;
    private ITaskExecutorRepository taskExecutorRepository;
    private ITaskExecutorContainerProvider<?> taskExecutorContainerDelegator;
    private TaskExecutorEventBusCoordinator taskExecutorEventBusCoordinator;
    public String getEngineName() { return engineName; }
    public void setEngineName(String name) { engineName = name; }
    public ITaskExecutorRepository getTaskExecutorRepository() { return taskExecutorRepository; }
    public void setTaskExecutorRepository(ITaskExecutorRepository repository) { taskExecutorRepository = repository; }
    public ITaskExecutorContainerProvider<?> getTaskExecutorContainerDelegator() { return taskExecutorContainerDelegator; }
    public void setTaskExecutorContainerDelegator(ITaskExecutorContainerProvider<?> container) { taskExecutorContainerDelegator = container; }
    public TaskExecutorEventBusCoordinator getTaskExecutorEventBusCoordinator() { return taskExecutorEventBusCoordinator; }
    public void setTaskExecutorEventBusCoordinator(TaskExecutorEventBusCoordinator events) { taskExecutorEventBusCoordinator = events; }
}
