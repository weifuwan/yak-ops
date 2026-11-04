package org.apache.flink.cdc.runtime.partitioning;

import org.apache.flink.api.common.functions.Partitioner;
import org.apache.flink.cdc.common.annotation.Internal;

/** Partitioner that send {@link PartitioningEvent} to its target partition. */
@Internal
public class EventPartitioner implements Partitioner<Integer> {
    @Override
    public int partition(Integer target, int numPartitions) {
        if (target >= numPartitions) {
            throw new IllegalStateException(
                    String.format(
                            "The target of the event %d is greater than number of downstream partitions %d",
                            target, numPartitions));
        }
        return target;
    }
}
