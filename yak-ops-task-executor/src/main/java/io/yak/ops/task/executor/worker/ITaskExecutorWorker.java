package io.yak.ops.task.executor.worker;

import io.yak.ops.task.executor.ITaskExecutor;

public interface ITaskExecutorWorker {

    int getId();

    void start();

    void registerTaskExecutor(final ITaskExecutor taskExecutor);

    void unRegisterTaskExecutor(final ITaskExecutor taskExecutor);

    void fireTaskExecutor(final ITaskExecutor taskExecutor);

    void unFireTaskExecutor(final ITaskExecutor taskExecutor);

    int getRegisteredTaskExecutorSize();

    int getFiredTaskExecutorSize();
}
