package io.yak.ops.worker.task.executor.container;

import io.yak.ops.worker.task.executor.ITaskExecutor;

public interface ITaskExecutorContainer extends AutoCloseable {
    void dispatch(ITaskExecutor executor);

    void start(ITaskExecutor executor);

    void pause(ITaskExecutor executor);

    void kill(ITaskExecutor executor);

    void finalize(ITaskExecutor executor);

    double slotUsage();

    @Override
    void close();
}
