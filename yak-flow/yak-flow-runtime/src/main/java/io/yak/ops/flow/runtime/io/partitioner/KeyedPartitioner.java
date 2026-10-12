package io.yak.ops.flow.runtime.io.partitioner;

import io.yak.ops.core.api.operators.KeySelector;
import io.yak.ops.flow.runtime.state.KeyGroupRangeAssignment;
import java.util.Objects;

/** Routes records by stable key-group identity to the assigned downstream subtask. */
public final class KeyedPartitioner<T> implements StreamPartitioner<T> {

    private final KeySelector<T> keySelector;
    private final int maxParallelism;

    public KeyedPartitioner(KeySelector<T> keySelector) {
        this(keySelector, 128);
    }

    public KeyedPartitioner(KeySelector<T> keySelector, int maxParallelism) {
        this.keySelector = Objects.requireNonNull(keySelector, "keySelector");
        KeyGroupRangeAssignment.checkMaxParallelism(maxParallelism);
        this.maxParallelism = maxParallelism;
    }

    public int getMaxParallelism() {
        return maxParallelism;
    }

    @Override
    public int selectChannel(T record, int numberOfChannels) throws Exception {
        if (numberOfChannels <= 0 || numberOfChannels > maxParallelism) {
            throw new IllegalArgumentException("KeyGroup parallelism is out of range");
        }
        Object key = Objects.requireNonNull(keySelector.getKey(record), "KEYED partitioning requires a non-null key");
        if (key.getClass().isArray()) {
            throw new IllegalArgumentException(
                    "KEYED partitioning does not support array keys because their default hash is identity-based");
        }
        return KeyGroupRangeAssignment.assignKeyToParallelOperator(key, maxParallelism, numberOfChannels);
    }
}
