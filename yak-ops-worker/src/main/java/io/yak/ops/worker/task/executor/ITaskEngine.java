package io.yak.ops.worker.task.executor;

import java.util.Collection;

public interface ITaskEngine extends AutoCloseable {
    void start();

    void submitTask(ITaskExecutor taskExecutor);

    void pauseTask(String taskExecutorId);

    void killTask(String taskExecutorId);

    Collection<ITaskExecutor> queryTaskExecutors();

    @Override
    void close();
}
