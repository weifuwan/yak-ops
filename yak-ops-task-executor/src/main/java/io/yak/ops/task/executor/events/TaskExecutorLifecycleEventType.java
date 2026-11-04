package io.yak.ops.task.executor.events;

public enum TaskExecutorLifecycleEventType {

    DISPATCHED,

    RUNNING,

    RUNTIME_CONTEXT_CHANGE,

    PAUSE,

    PAUSED,

    KILL,

    KILLED,

    SUCCESS,

    FAILED,

    FINALIZE,
    ;

    public boolean isFinished() {
        return (this == KILLED
                || this == PAUSED
                || this == FAILED
                || this == SUCCESS);
    }
}
