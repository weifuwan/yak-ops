package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;

/** Reader 向 Enumerator 请求 Split，携带当前 Subtask 与 Attempt 身份。 */
public record RequestSplitEvent(int subtaskId, int attemptNumber) implements OperatorEvent {

    public RequestSplitEvent {
        if (subtaskId < 0 || attemptNumber < 0) {
            throw new IllegalArgumentException("Split 请求身份不能为负数");
        }
    }
}
