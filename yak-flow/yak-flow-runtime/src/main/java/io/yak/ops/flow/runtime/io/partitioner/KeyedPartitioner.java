package io.yak.ops.flow.runtime.io.partitioner;

import io.yak.ops.core.api.operators.KeySelector;
import java.util.Objects;

/**
 * Preserve existing KEYED routing semantics until a separate KeyGroup/state-rescale contract.
 * This is not Flink's KeyGroupRangeAssignment.
 */
public final class KeyedPartitioner<T> implements StreamPartitioner<T> {

    private final KeySelector<T> keySelector;

    public KeyedPartitioner(KeySelector<T> keySelector) {
        this.keySelector = Objects.requireNonNull(keySelector, "keySelector");
    }

    @Override
    public int selectChannel(T record, int numberOfChannels) throws Exception {
        if (numberOfChannels <= 0) {
            throw new IllegalArgumentException("No target channels");
        }
        Object key = Objects.requireNonNull(keySelector.getKey(record), "KEYED 路由不能使用 null 键");
        if (key.getClass().isArray()) {
            throw new IllegalArgumentException("KEYED 不允许数组键（默认数组哈希与业务键不一致）");
        }
        return Math.floorMod(key.hashCode(), numberOfChannels);
    }
}
