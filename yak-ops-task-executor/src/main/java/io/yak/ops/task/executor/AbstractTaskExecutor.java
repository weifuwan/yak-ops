package io.yak.ops.task.executor;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.log.TaskInstanceLogHeader;
import io.yak.ops.plugin.task.api.log.TaskLogMarkers;
import io.yak.ops.task.executor.eventbus.TaskExecutorEventBus;
import io.yak.ops.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.task.executor.exceptions.TaskExecutorRuntimeException;

import java.util.concurrent.atomic.AtomicReference;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTaskExecutor implements ITaskExecutor {

    private static final long DEFAULT_TRACK_INTERVAL = 10_000;

    private long latestStateTrackTime;

    private boolean startFlag;

    protected final AtomicReference<TaskExecutorState> taskExecutorState = new AtomicReference<>();

    protected final TaskExecutionContext taskExecutionContext;

    protected final TaskExecutorEventBus taskExecutorEventBus;

    public AbstractTaskExecutor(final TaskExecutionContext taskExecutionContext,
                                final TaskExecutorEventBus taskExecutorEventBus) {
        this.taskExecutionContext = taskExecutionContext;
        this.taskExecutorEventBus = taskExecutorEventBus;
        this.latestStateTrackTime = 0;
        this.startFlag = false;
        transitTaskExecutorState(TaskExecutorState.INITIALIZED);
    }

    @SneakyThrows
    @Override
    public void start() {
        if (startFlag) {
            throw new TaskExecutorRuntimeException(this + " has already started");
        }
        startFlag = true;
        TaskInstanceLogHeader.printInitializeTaskContextHeader();
        initializeTaskContext();

        publishTaskRunningEvent();

        TaskInstanceLogHeader.printLoadTaskInstancePluginHeader();
        initializeTaskPlugin();

        TaskInstanceLogHeader.printExecuteTaskHeader();

        if (taskExecutionContext.getDryRun() == Flag.YES.getCode()) {
            log.info("The task: {} is dryRun model, skip the trigger stage.", taskExecutionContext.getTaskName());
            transitTaskExecutorState(TaskExecutorState.SUCCEEDED);
            return;
        }

        doTriggerTaskPlugin();
    }

    @Override
    public boolean isStarted() {
        return startFlag;
    }

    @Override
    public TaskExecutorState trackTaskExecutorState() {
        final TaskExecutorState state = taskExecutorState.get();
        if (state.isFinalState()) {
            return state;
        }
        latestStateTrackTime = System.currentTimeMillis();
        transitTaskExecutorState(doTrackTaskPluginStatus());
        return taskExecutorState.get();
    }

    @Override
    public long getRemainingTrackDelay() {
        if (latestStateTrackTime == 0) {
            return 0;
        }
        return latestStateTrackTime + DEFAULT_TRACK_INTERVAL - System.currentTimeMillis();
    }

    @Override
    public TaskExecutorEventBus getTaskExecutorEventBus() {
        return taskExecutorEventBus;
    }

    @Override
    public TaskExecutionContext getTaskExecutionContext() {
        return taskExecutionContext;
    }

    protected void transitTaskExecutorState(final TaskExecutorState taskExecutorState) {
        this.taskExecutorState.set(taskExecutorState);
    }

    protected abstract TaskExecutorState doTrackTaskPluginStatus();

    protected abstract void initializeTaskPlugin();

    protected abstract void doTriggerTaskPlugin();

    protected void initializeTaskContext() {
        taskExecutionContext.setStartTime(System.currentTimeMillis());
        log.info(TaskLogMarkers.excludeInTaskLog(), "Initialized taskContext {}",
                JSONUtils.toPrettyJsonString(taskExecutionContext));
    }

    private void publishTaskRunningEvent() {
        transitTaskExecutorState(TaskExecutorState.RUNNING);
        taskExecutorEventBus.publish(TaskExecutorStartedLifecycleEvent.of(this));
    }

}
