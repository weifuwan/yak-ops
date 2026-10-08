package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 跟踪最近一次成功 Checkpoint 之后的分片分配。
 *
 * <p>所有方法必须由 SourceCoordinator 线程调用。分片进入 Reader 邮箱时记录，
 * 不以 Reader 收到事件作为 Checkpoint 成功的依据。
 *
 * <p>这里保存的是协调侧分配历史，完整恢复仍依赖 Reader 快照和数据通道状态。
 */
public final class SplitAssignmentTracker<SplitT extends SourceSplit> {

    private final Map<Integer, LinkedHashMap<String, SplitT>> outstanding = new LinkedHashMap<>();
    private final NavigableMap<Long, Map<Integer, List<SplitT>>> checkpointSnapshots = new TreeMap<>();

    /** 注册尚未被完整成功 Checkpoint 覆盖的分片分配。 */
    public void recordAssignment(int subtaskId, SplitT split) {
        if (subtaskId < 0 || split == null || split.splitId() == null || split.splitId().isBlank()) {
            throw new IllegalArgumentException("分片分配参数无效");
        }
        LinkedHashMap<String, SplitT> splits =
                outstanding.computeIfAbsent(subtaskId, ignored -> new LinkedHashMap<>());
        if (splits.putIfAbsent(split.splitId(), split) != null) {
            throw new IllegalArgumentException("分片尚未 Checkpoint 就发生重复分配：" + split.splitId());
        }
    }

    /** 为一个 Checkpoint 冻结截至此刻尚未被成功确认的分片分配。 */
    public Map<Integer, List<SplitT>> snapshot(long checkpointId) {
        if (checkpointId < 0 || checkpointSnapshots.containsKey(checkpointId)) {
            throw new IllegalArgumentException("checkpointId 无效或重复");
        }
        Map<Integer, List<SplitT>> snapshot = new LinkedHashMap<>();
        outstanding.forEach((id, splits) -> snapshot.put(id, List.copyOf(splits.values())));
        Map<Integer, List<SplitT>> frozen = Map.copyOf(snapshot);
        checkpointSnapshots.put(checkpointId, frozen);
        return frozen;
    }

    /** 只有完整 Checkpoint 成功后才移除它覆盖的分配历史。 */
    public void notifyCheckpointComplete(long checkpointId) {
        Map<Integer, List<SplitT>> covered = checkpointSnapshots.get(checkpointId);
        if (covered == null) {
            throw new IllegalArgumentException("未知的 Coordinator Checkpoint：" + checkpointId);
        }
        covered.forEach((id, splits) -> {
            Map<String, SplitT> current = outstanding.get(id);
            if (current != null) {
                for (SplitT split : splits) {
                    current.remove(split.splitId());
                }
                if (current.isEmpty()) {
                    outstanding.remove(id);
                }
            }
        });
        checkpointSnapshots.headMap(checkpointId, true).clear();
    }

    /** 取消一次尚未成功的 Checkpoint，保留分配历史供后续快照使用。 */
    public void notifyCheckpointAborted(long checkpointId) {
        checkpointSnapshots.remove(checkpointId);
    }

    /** 获取只读的分配历史快照，用于诊断和后续故障恢复。 */
    public Map<Integer, List<SplitT>> outstandingAssignments() {
        Map<Integer, List<SplitT>> copy = new LinkedHashMap<>();
        outstanding.forEach((id, splits) -> copy.put(id, new ArrayList<>(splits.values())));
        copy.replaceAll((id, splits) -> List.copyOf(splits));
        return Map.copyOf(copy);
    }
}
