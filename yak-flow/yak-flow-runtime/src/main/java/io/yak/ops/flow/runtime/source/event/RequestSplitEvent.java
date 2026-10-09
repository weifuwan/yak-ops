package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;

/** Reader split request carrying its subtask index and execution-attempt identity. */
public record RequestSplitEvent(int subtaskId, int attemptNumber) implements OperatorEvent {

    public RequestSplitEvent {
        if (subtaskId < 0 || attemptNumber < 0) {
            throw new IllegalArgumentException("Split 请求身份不能为负数");
        }
    }
}
