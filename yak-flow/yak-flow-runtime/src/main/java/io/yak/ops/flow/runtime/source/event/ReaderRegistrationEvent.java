package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;

/** Reader Task 向 Coordinator 注册的控制事件。 */
public record ReaderRegistrationEvent(int subtaskId) implements OperatorEvent {
    public ReaderRegistrationEvent {
        if (subtaskId < 0) {
            throw new IllegalArgumentException("subtaskId 不能为负数");
        }
    }
}
