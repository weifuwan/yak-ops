package io.yak.ops.task.executor;

public interface ITaskExecutorStateTracker {

    /**
     * Track the state of the task executor.
     */
    TaskExecutorState trackTaskExecutorState();

    /**
     * Get the next track time delay in ms.
     */
    long getRemainingTrackDelay();

}
