package io.yak.ops.task.executor;

import lombok.Getter;

public enum TaskExecutorState {

    INITIALIZED(false),

    RUNNING(false),

    PAUSED(true),

    KILLED(true),

    FAILED(true),

    SUCCEEDED(true),

    ;

    @Getter
    private final boolean finalState;

    TaskExecutorState(boolean finalState) {
        this.finalState = finalState;
    }

}
