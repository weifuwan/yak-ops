package io.yak.ops.worker.task.executor.events;

public enum TaskExecutorLifecycleEventType {
    DISPATCHED, RUNNING, RUNTIME_CONTEXT_CHANGE, PAUSE, PAUSED, KILL, KILLED, SUCCESS, FAILED, FINALIZE;
    public boolean isFinished() {
        return this == PAUSED || this == KILLED || this == SUCCESS || this == FAILED;
    }
}
