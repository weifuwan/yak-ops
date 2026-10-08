package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;

/** Reader 注册身份：Subtask Index 与 Attempt Number 必须同时匹配。 */
public record ReaderRegistrationEvent(int subtaskId, int attemptNumber) implements OperatorEvent {

    public ReaderRegistrationEvent {
        if (subtaskId < 0 || attemptNumber < 0) {
            throw new IllegalArgumentException("Reader 注册身份不能为负数");
        }
    }
}
