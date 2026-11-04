package io.yak.ops.server.master.engine.task.lifecycle.event;

import static com.google.common.base.Preconditions.checkState;

import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.plugin.task.api.enums.TaskTimeoutStrategy;
import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import java.util.concurrent.TimeUnit;

import lombok.Getter;

@Getter
public class TaskTimeoutLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    private final TaskTimeoutStrategy timeoutStrategy;

    protected TaskTimeoutLifecycleEvent(final ITaskExecution taskExecution,
                                        final TaskTimeoutStrategy timeoutStrategy,
                                        final long timeout) {
        super(timeout);
        this.timeoutStrategy = timeoutStrategy;
        this.taskExecution = taskExecution;
    }

    public static TaskTimeoutLifecycleEvent of(final ITaskExecution taskExecution,
                                               final TaskTimeoutStrategy timeoutStrategy,
                                               final long timeoutInMinutes) {
        final TaskInstance taskInstance = taskExecution.getTaskInstance();

        checkState(timeoutStrategy != null, "The task timeoutStrategy must not be null");
        checkState(timeoutInMinutes > 0, "The task timeout: %s must > 0 minutes", timeoutInMinutes);

        long delayTime = System.currentTimeMillis() - taskInstance.getSubmitTime().getTime()
                + TimeUnit.MINUTES.toMillis(timeoutInMinutes);
        return new TaskTimeoutLifecycleEvent(taskExecution, timeoutStrategy, delayTime);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.TIMEOUT;
    }

    @Override
    public String toString() {
        return "TaskRetryEvent{" +
                "task=" + taskExecution.getName() +
                ", timeout=" + delayTime +
                '}';
    }
}
