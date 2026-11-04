package io.yak.ops.task.executor.worker;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.TaskExecutorState;
import io.yak.ops.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorSuccessLifecycleEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTaskExecutorWorker implements ITaskExecutorWorker {

    protected void trackTaskExecutorState(final ITaskExecutor taskExecutor) {
        checkNotNull(taskExecutor, "taskExecutor is null");

        final TaskExecutorState taskExecutorState = taskExecutor.trackTaskExecutorState();

        switch (taskExecutorState) {
            case SUCCEEDED:
                onTaskExecutorSuccess(taskExecutor);
                break;
            case FAILED:
                onTaskExecutorFailed(taskExecutor);
                break;
            case KILLED:
                onTaskExecutorKilled(taskExecutor);
                break;
            case PAUSED:
                onTaskExecutorPaused(taskExecutor);
                break;
            case RUNNING:
                log.debug("TaskExecutor(id={}) is running", taskExecutor.getId());
                break;
            default:
                throw new IllegalStateException("Unexpected TaskExecutorState: " + taskExecutorState
                        + " for taskExecutor(id=" + taskExecutor.getId() + ")");
        }
    }

    protected void onTaskExecutorSuccess(final ITaskExecutor taskExecutor) {
        taskExecutor.getTaskExecutionContext().setEndTime(System.currentTimeMillis());
        taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorSuccessLifecycleEvent.of(taskExecutor));
        onTaskExecutorFinished(taskExecutor);
    }

    protected void onTaskExecutorKilled(final ITaskExecutor taskExecutor) {
        taskExecutor.getTaskExecutionContext().setEndTime(System.currentTimeMillis());
        taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorKilledLifecycleEvent.of(taskExecutor));
        onTaskExecutorFinished(taskExecutor);
    }

    protected void onTaskExecutorPaused(final ITaskExecutor taskExecutor) {
        taskExecutor.getTaskExecutionContext().setEndTime(System.currentTimeMillis());
        taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorPausedLifecycleEvent.of(taskExecutor));
        onTaskExecutorFinished(taskExecutor);
    }

    protected void onTaskExecutorFailed(final ITaskExecutor taskExecutor) {
        taskExecutor.getTaskExecutionContext().setEndTime(System.currentTimeMillis());
        taskExecutor.getTaskExecutorEventBus().publish(TaskExecutorFailedLifecycleEvent.of(taskExecutor));
        onTaskExecutorFinished(taskExecutor);
    }

    protected void onTaskExecutorFinished(final ITaskExecutor taskExecutor) {
        unFireTaskExecutor(taskExecutor);
    }

}
