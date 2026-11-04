package io.yak.ops.task.executor.container;

import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.worker.TaskExecutorWorker;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * The container to execute a {@link ITaskExecutor} with shared thread mode.
 */
@Slf4j
public class SharedThreadTaskExecutorContainer extends AbstractTaskExecutorContainer {

    public SharedThreadTaskExecutorContainer(final TaskExecutorContainerConfig taskExecutorContainerConfig) {
        super(taskExecutorContainerConfig);
    }

    @Override
    protected Optional<TaskExecutorWorker> getTaskExecutorWorkerCandidate(final ITaskExecutor taskExecutor) {
        return Optional.of(taskExecutorWorkers.roundRobinSelectWorker());
    }

}
