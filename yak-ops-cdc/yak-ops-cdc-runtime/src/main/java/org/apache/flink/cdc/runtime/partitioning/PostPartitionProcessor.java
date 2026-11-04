package org.apache.flink.cdc.runtime.partitioning;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.event.Event;

/**
 * Function for unloading {@link Event} from internal {@link PartitioningEvent} after {@link
 * EventPartitioner}.
 */
@Internal
public class PostPartitionProcessor extends RichMapFunction<PartitioningEvent, Event> {
    @Override
    public Event map(PartitioningEvent value) throws Exception {
        return value.getPayload();
    }
}
