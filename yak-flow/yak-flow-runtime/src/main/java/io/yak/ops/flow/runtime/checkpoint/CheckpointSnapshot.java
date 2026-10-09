package io.yak.ops.flow.runtime.checkpoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, aligned snapshot of one local job's Source, Reader and operator state.
 *
 * <p>Contains versioned serialized state only, never live readers, task threads,
 * connections or in-flight channel messages.
 */
public record CheckpointSnapshot(
        long checkpointId,
        String graphSignature,
        SerializedState enumeratorState,
        Map<Integer, List<SerializedState>> readerSplits,
        Map<Integer, List<SerializedState>> assignments,
        long completedAtMillis,
        Map<OperatorSubtask, Map<String, SerializedState>> operatorStates) {

    public CheckpointSnapshot {
        if (checkpointId <= 0 || completedAtMillis <= 0) {
            throw new IllegalArgumentException("Checkpoint ID 与完成时间必须为正数");
        }
        if (graphSignature == null || graphSignature.isBlank()) {
            throw new IllegalArgumentException("Checkpoint 拓扑指纹不能为空");
        }
        Objects.requireNonNull(enumeratorState, "enumeratorState 不能为空");
        readerSplits = freeze(readerSplits);
        assignments = freeze(assignments);
        Objects.requireNonNull(operatorStates, "operatorStates");
        Map<OperatorSubtask, Map<String, SerializedState>> copy = new LinkedHashMap<>();
        operatorStates.forEach((key, states) -> {
            Objects.requireNonNull(key, "OperatorSubtask");
            Objects.requireNonNull(states, "operator states");
            if (copy.putIfAbsent(key, Map.copyOf(states)) != null) {
                throw new IllegalArgumentException("Duplicate operator subtask");
            }
            states.keySet().forEach(name -> {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("State name must not be blank");
                }
            });
        });
        operatorStates = Map.copyOf(copy);
    }

    /** Backwards compatible constructor for Source/Sink-only format v1. */
    public CheckpointSnapshot(
            long checkpointId,
            String graphSignature,
            SerializedState enumeratorState,
            Map<Integer, List<SerializedState>> readerSplits,
            Map<Integer, List<SerializedState>> assignments,
            long completedAtMillis) {
        this(checkpointId, graphSignature, enumeratorState, readerSplits, assignments, completedAtMillis, Map.of());
    }

    /** Stable logical operator identity, never a graph-local numeric operator ID. */
    public record OperatorSubtask(String uid, int subtaskIndex) {
        public OperatorSubtask {
            if (uid == null || uid.isBlank() || subtaskIndex < 0) {
                throw new IllegalArgumentException("Invalid checkpoint operator identity");
            }
        }
    }

    private static Map<Integer, List<SerializedState>> freeze(Map<Integer, List<SerializedState>> input) {
        Objects.requireNonNull(input, "Checkpoint 子任务状态不能为空");
        Map<Integer, List<SerializedState>> copy = new LinkedHashMap<>();
        input.forEach((index, splits) -> {
            if (index == null || index < 0) {
                throw new IllegalArgumentException("Checkpoint subtaskIndex 无效");
            }
            copy.put(index, List.copyOf(splits));
        });
        return Map.copyOf(copy);
    }

    /** Versioned state bytes copied on construction and on access to prevent mutation. */
    public record SerializedState(int version, byte[] bytes) {

        public SerializedState {
            if (version < 0) {
                throw new IllegalArgumentException("State serializer version 不能为负数");
            }
            bytes = Objects.requireNonNull(bytes, "状态字节不能为空").clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
