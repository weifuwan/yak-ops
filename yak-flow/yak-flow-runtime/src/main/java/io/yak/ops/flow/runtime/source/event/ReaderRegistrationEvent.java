package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;

/** Identity of one Reader registration, including subtask and active attempt number. */
public record ReaderRegistrationEvent(int subtaskId, int attemptNumber) implements OperatorEvent {

    public ReaderRegistrationEvent {
        if (subtaskId < 0 || attemptNumber < 0) {
            throw new IllegalArgumentException("Reader 注册身份不能为负数");
        }
    }
}
