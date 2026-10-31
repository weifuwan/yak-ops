package io.yak.ops.flow.runtime.io.partitioner;

import io.yak.ops.flow.runtime.partitioning.PartitioningEvent;

/** Sends a CDC event to the exact subtask selected before the network exchange. */
public final class EventPartitioner<T> implements StreamPartitioner<T> {

    @Override
    public int selectChannel(T record, int numberOfChannels) {
        if (!(record instanceof PartitioningEvent event)) {
            throw new IllegalArgumentException("EVENT partitioning requires a PartitioningEvent");
        }
        int target = event.getTargetPartition();
        if (numberOfChannels <= 0 || target < 0 || target >= numberOfChannels) {
            throw new IllegalArgumentException("Event target partition is outside the downstream parallelism");
        }
        return target;
    }
}
