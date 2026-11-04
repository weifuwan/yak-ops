package io.yak.ops.server.master.engine.task.lifecycle.event;

import static com.google.common.base.Preconditions.checkState;

import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import java.util.concurrent.TimeUnit;

import lombok.Getter;

@Getter
public class TaskRetryLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    protected TaskRetryLifecycleEvent(final ITaskExecution taskExecution,
                                      final long delayTime) {
        super(delayTime);
        this.taskExecution = taskExecution;
    }

    public static TaskRetryLifecycleEvent of(final ITaskExecution taskExecution) {
        final TaskInstance taskInstance = taskExecution.getTaskInstance();
        checkState(taskInstance != null, "The task instance must be initialized before retrying.");
        final int retryInterval = taskInstance.getRetryInterval();

        final int retryTimes = taskInstance.getRetryTimes();
        final int maxRetryTimes = taskInstance.getMaxRetryTimes();
        checkState(retryTimes < maxRetryTimes,
                "The task retry times: %s must smaller then maxRetryTimes: %s.",
                retryTimes,
                maxRetryTimes);
        // The task should be retried at (endTime + retryInterval), so the remaining delay is the retry interval
        // minus the time which has already elapsed since the task ended. When the retry interval has already
        // passed, e.g. the event is created during failover, the task should be retried immediately.
        final long elapsedTime = System.currentTimeMillis() - taskInstance.getEndTime().getTime();
        final long remainingTime = Math.max(0, TimeUnit.MINUTES.toMillis(retryInterval) - elapsedTime);
        return new TaskRetryLifecycleEvent(taskExecution, remainingTime);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.RETRY;
    }

    @Override
    public String toString() {
        return "TaskRetryLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                ", delayTime=" + delayTime + "/ms" +
                '}';
    }
}
