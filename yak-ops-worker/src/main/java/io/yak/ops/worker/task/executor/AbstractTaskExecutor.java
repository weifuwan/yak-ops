package io.yak.ops.worker.task.executor;

import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBus;
import io.yak.ops.worker.task.executor.events.TaskExecutorStartedLifecycleEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** DolphinScheduler AbstractTaskExecutor; the plugin's asynchronous completion drives state. */
public abstract class AbstractTaskExecutor implements ITaskExecutor {
    private static final long DEFAULT_TRACK_INTERVAL = 10_000;
    private volatile long latestStateTrackTime;
    private final AtomicBoolean started = new AtomicBoolean();
    protected final AtomicReference<TaskExecutorState> taskExecutorState =
            new AtomicReference<>(TaskExecutorState.INITIALIZED);
    protected final TaskExecutionContext taskExecutionContext;
    protected final TaskExecutorEventBus taskExecutorEventBus;

    protected AbstractTaskExecutor(TaskExecutionContext context, TaskExecutorEventBus eventBus) {
        taskExecutionContext = context;
        taskExecutorEventBus = eventBus;
    }

    @Override
    public final void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Task executor already started: " + getId());
        }
        initializeTaskContext();
        initializeTaskPlugin();
        transitTaskExecutorState(TaskExecutorState.RUNNING);
        taskExecutorEventBus.publish(TaskExecutorStartedLifecycleEvent.of(this));
        doTriggerTaskPlugin();
    }

    @Override public boolean isStarted() { return started.get(); }

    @Override public TaskExecutorState trackTaskExecutorState() {
        TaskExecutorState state = taskExecutorState.get();
        if (state.isFinalState()) return state;
        latestStateTrackTime = System.currentTimeMillis();
        TaskExecutorState observed = doTrackTaskPluginStatus();
        transitTaskExecutorState(observed);
        return taskExecutorState.get();
    }

    @Override
    public long getRemainingTrackDelay() {
        if (taskExecutorState.get().isFinalState()) return 0;
        if (latestStateTrackTime == 0) return 0;
        return latestStateTrackTime + DEFAULT_TRACK_INTERVAL - System.currentTimeMillis();
    }

    protected void transitTaskExecutorState(TaskExecutorState state) {
        taskExecutorState.set(state);
    }

    protected void initializeTaskContext() { taskExecutionContext.setStartTime(System.currentTimeMillis()); }
    protected abstract void initializeTaskPlugin();
    protected abstract void doTriggerTaskPlugin();
    protected abstract TaskExecutorState doTrackTaskPluginStatus();

    @Override public TaskExecutionContext getTaskExecutionContext() { return taskExecutionContext; }
    @Override public TaskExecutorEventBus getTaskExecutorEventBus() { return taskExecutorEventBus; }
}
