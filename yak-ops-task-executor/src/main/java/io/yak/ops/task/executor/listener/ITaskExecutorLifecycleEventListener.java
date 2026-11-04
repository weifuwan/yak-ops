package io.yak.ops.task.executor.listener;

import io.yak.ops.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorFinalizeLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorKillLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorPauseLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorSuccessLifecycleEvent;

public interface ITaskExecutorLifecycleEventListener {

    void onTaskExecutorDispatchedLifecycleEvent(final TaskExecutorDispatchedLifecycleEvent event);

    void onTaskExecutorStartedLifecycleEvent(final TaskExecutorStartedLifecycleEvent event);

    void onTaskExecutorRuntimeContextChangedEvent(final TaskExecutorRuntimeContextChangedLifecycleEvent event);

    void onTaskExecutorPauseLifecycleEvent(final TaskExecutorPauseLifecycleEvent event);

    void onTaskExecutorPausedLifecycleEvent(final TaskExecutorPausedLifecycleEvent event);

    void onTaskExecutorKillLifecycleEvent(final TaskExecutorKillLifecycleEvent event);

    void onTaskExecutorKilledLifecycleEvent(final TaskExecutorKilledLifecycleEvent event);

    void onTaskExecutorSuccessLifecycleEvent(final TaskExecutorSuccessLifecycleEvent event);

    void onTaskExecutorFailLifecycleEvent(final TaskExecutorFailedLifecycleEvent event);

    void onTaskExecutorFinalizeLifecycleEvent(final TaskExecutorFinalizeLifecycleEvent event);

}
