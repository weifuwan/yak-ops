package io.yak.ops.flow.runtime.source.event;

/** Reader Task 向 Coordinator 注册的控制事件。 */
public record ReaderRegistrationEvent(int subtaskId) {
    public ReaderRegistrationEvent {
        if (subtaskId < 0) {
            throw new IllegalArgumentException("subtaskId 不能为负数");
        }
    }
}
