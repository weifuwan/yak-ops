package io.yak.ops.task.executor.eventbus;

import io.yak.ops.task.executor.listener.ITaskExecutorLifecycleEventListener;

/**
 * The coordinator responsible to fire the {@link  TaskExecutorEventBus}.
 */
public interface ITaskExecutorEventBusCoordinator extends AutoCloseable {

    /**
     * Start the coordinator.
     */
    void start();

    /**
     * Register the task executor event listener
     */
    void registerTaskExecutorLifecycleEventListener(final ITaskExecutorLifecycleEventListener taskExecutorLifecycleEventListener);

    /**
     * Shutdown the coordinator
     */
    @Override
    void close();

}
