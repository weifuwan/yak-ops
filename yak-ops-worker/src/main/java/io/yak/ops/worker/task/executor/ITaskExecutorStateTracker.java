package io.yak.ops.worker.task.executor;

/** DolphinScheduler-style physical task state observation contract. */
public interface ITaskExecutorStateTracker {
    TaskExecutorState trackTaskExecutorState();

    long getRemainingTrackDelay();
}
