package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.List;
import java.util.Objects;

/** Coordinator 向 SourceOperator 发送的分片交付事件。 */
public record AddSplitEvent<SplitT extends SourceSplit>(List<SplitT> splits) {

    public AddSplitEvent {
        Objects.requireNonNull(splits, "splits 不能为空");
        if (splits.isEmpty()) {
            throw new IllegalArgumentException("分片集合不能为空");
        }
        splits = List.copyOf(splits);
        for (SplitT split : splits) {
            if (split.splitId() == null || split.splitId().isBlank()) {
                throw new IllegalArgumentException("Split ID 不能为空");
            }
        }
    }
}
