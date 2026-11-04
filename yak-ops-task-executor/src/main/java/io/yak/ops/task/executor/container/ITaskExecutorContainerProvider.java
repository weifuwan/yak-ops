package io.yak.ops.task.executor.container;

public interface ITaskExecutorContainerProvider<T extends ITaskExecutorContainer> {

    T getExecutorContainer();

}
