package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Source 协调侧的一份检查点片段，不能单独代表作业检查点成功。
 *
 * <p>Runtime 必须将本片段、所有 Reader 状态和下游状态按同一检查点边界
 * 一起持久化成功后，才能通知 Source Checkpoint 完成。
 *
 * <p>分片内容由 Connector 提供，必须是可独立序列化和恢复的状态快照。
 */
public record SourceCoordinatorCheckpoint<SplitT extends SourceSplit, EnumStateT>(
        long checkpointId,
        EnumStateT enumeratorState,
        Map<Integer, List<SplitT>> assignedSinceLastCompletedCheckpoint) {

    public SourceCoordinatorCheckpoint {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId 不能为负数");
        }
        Objects.requireNonNull(enumeratorState, "enumeratorState 不能为空");
        Objects.requireNonNull(assignedSinceLastCompletedCheckpoint, "assignedSinceLastCompletedCheckpoint 不能为空");
        Map<Integer, List<SplitT>> copy = new LinkedHashMap<>();
        assignedSinceLastCompletedCheckpoint.forEach((id, splits) -> {
            if (id == null || id < 0) {
                throw new IllegalArgumentException("子任务编号无效");
            }
            copy.put(id, List.copyOf(splits));
        });
        assignedSinceLastCompletedCheckpoint = Map.copyOf(copy);
    }
}
