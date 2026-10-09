package io.yak.ops.flow.runtime.checkpoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 某个本地作业已对齐的 Source/Reader 状态切面；仅记录版本化二进制状态，
 * 不持有活动 Reader、连接、Task 或未完成的 Channel 消息。
 */
public record LocalCheckpointState(
        long checkpointId,
        String graphSignature,
        SerializedState enumeratorState,
        Map<Integer, List<SerializedState>> readerSplits,
        Map<Integer, List<SerializedState>> assignments,
        long completedAtMillis) {

    public LocalCheckpointState {
        if (checkpointId <= 0 || completedAtMillis <= 0) {
            throw new IllegalArgumentException("Checkpoint ID 与完成时间必须为正数");
        }
        if (graphSignature == null || graphSignature.isBlank()) {
            throw new IllegalArgumentException("Checkpoint 拓扑指纹不能为空");
        }
        Objects.requireNonNull(enumeratorState, "enumeratorState 不能为空");
        readerSplits = freeze(readerSplits);
        assignments = freeze(assignments);
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

    /** 每个状态携带独立版本和不可变字节内容；不会泄露可修改的内部数组。 */
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
