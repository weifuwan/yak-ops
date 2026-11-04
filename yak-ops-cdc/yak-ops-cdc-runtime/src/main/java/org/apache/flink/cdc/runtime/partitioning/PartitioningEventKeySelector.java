package org.apache.flink.cdc.runtime.partitioning;

import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.cdc.common.annotation.Internal;

/** Key selector for {@link PartitioningEvent}. */
@Internal
public class PartitioningEventKeySelector implements KeySelector<PartitioningEvent, Integer> {
    @Override
    public Integer getKey(PartitioningEvent event) {
        return event.getTargetPartition();
    }
}
