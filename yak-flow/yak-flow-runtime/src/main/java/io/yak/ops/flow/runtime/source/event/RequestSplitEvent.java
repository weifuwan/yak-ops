package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;

/** Reader 向 Enumerator 请求更多分片的控制事件。 */
public record RequestSplitEvent(int subtaskId) implements OperatorEvent {
    public RequestSplitEvent {
        if (subtaskId < 0) {
            throw new IllegalArgumentException("subtaskId 不能为负数");
        }
    }
}
