package io.yak.ops.task.executor.container;

import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.worker.TaskExecutorWorker;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * The container to execute a {@link ITaskExecutor} with exclusive thread mode.
 * <p> Each task executor will be executed by a single thread.
 */
@Slf4j
public class ExclusiveThreadTaskExecutorContainer extends AbstractTaskExecutorContainer {

    public ExclusiveThreadTaskExecutorContainer(final TaskExecutorContainerConfig taskExecutorContainerConfig) {
        super(taskExecutorContainerConfig);
    }

    @Override
    protected Optional<TaskExecutorWorker> getTaskExecutorWorkerCandidate(final ITaskExecutor taskExecutor) {
        return taskExecutorWorkers.selectIdleWorker();
    }

}
