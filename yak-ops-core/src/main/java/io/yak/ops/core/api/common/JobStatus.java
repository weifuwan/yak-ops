package io.yak.ops.core.api.common;

/** 单次提交作业的生命周期状态，与批处理或流处理模式无关。 */
public enum JobStatus {
    CREATED,
    RUNNING,
    FAILING,
    FAILED,
    CANCELLING,
    CANCELED,
    FINISHED;

    public boolean isTerminalState() {
        return this == FAILED || this == CANCELED || this == FINISHED;
    }
}
