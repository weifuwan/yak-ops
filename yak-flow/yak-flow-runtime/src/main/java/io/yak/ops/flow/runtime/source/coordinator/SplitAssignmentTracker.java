package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Records split assignments since the last successfully completed checkpoint.
 *
 * <p>Only the coordinator thread may mutate this state. Split delivery acknowledgement
 * is not a completed checkpoint; recovery also depends on Reader snapshots.
 */
public final class SplitAssignmentTracker<SplitT extends SourceSplit> {

    private final Map<Integer, LinkedHashMap<String, SplitT>> outstanding = new LinkedHashMap<>();
    private final NavigableMap<Long, Map<Integer, List<SplitT>>> checkpointSnapshots = new TreeMap<>();

    /** Records an assignment not yet covered by a completed checkpoint. */
    public void recordAssignment(int subtaskId, SplitT split) {
        if (subtaskId < 0
                || split == null
                || split.splitId() == null
                || split.splitId().isBlank()) {
            throw new IllegalArgumentException("分片分配参数无效");
        }
        LinkedHashMap<String, SplitT> splits = outstanding.computeIfAbsent(subtaskId, ignored -> new LinkedHashMap<>());
        if (splits.putIfAbsent(split.splitId(), split) != null) {
            throw new IllegalArgumentException("分片尚未 Checkpoint 就发生重复分配：" + split.splitId());
        }
    }

    /** Freezes outstanding assignments at this checkpoint boundary. */
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

    /** Removes assignment history only after its checkpoint is durably committed. */
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

    /** Discards a failed checkpoint attempt while retaining assignments for later snapshots. */
    public void notifyCheckpointAborted(long checkpointId) {
        checkpointSnapshots.remove(checkpointId);
    }

    /** Returns a detached view of outstanding assignments for diagnostics and recovery. */
    public Map<Integer, List<SplitT>> outstandingAssignments() {
        Map<Integer, List<SplitT>> copy = new LinkedHashMap<>();
        outstanding.forEach((id, splits) -> copy.put(id, new ArrayList<>(splits.values())));
        copy.replaceAll((id, splits) -> List.copyOf(splits));
        return Map.copyOf(copy);
    }
}
