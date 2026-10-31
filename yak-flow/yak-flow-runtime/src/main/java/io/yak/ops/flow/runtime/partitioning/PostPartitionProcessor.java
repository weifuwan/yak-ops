package io.yak.ops.flow.runtime.partitioning;

import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.event.Event;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import java.util.Objects;

/** Removes the routing envelope after the event reaches its destination subtask. */
public final class PostPartitionProcessor implements OneInputStreamOperator<PartitioningEvent, Event> {

    @Override
    public void processElement(PartitioningEvent element, Collector<Event> output) throws Exception {
        Objects.requireNonNull(output, "output")
                .collect(Objects.requireNonNull(element, "element").getPayload());
    }
}
