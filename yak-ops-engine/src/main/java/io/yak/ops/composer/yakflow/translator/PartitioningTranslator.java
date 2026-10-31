package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.partitioning.PartitioningEvent;
import io.yak.ops.flow.runtime.partitioning.PostPartitionProcessor;
import io.yak.ops.flow.runtime.partitioning.RegularPrePartitionOperator;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;

/** Broadcasts metadata and routes data with the Sink's table/key hash contract. */
public final class PartitioningTranslator {
    public Transformation<Event> translate(
            Transformation<Event> input,
            DataSink sink,
            int sourceParallelism,
            int sinkParallelism,
            String definitionId) {
        OneInputTransformation<Event, PartitioningEvent> pre = new OneInputTransformation<>(
                input,
                "PrePartition",
                () -> new RegularPrePartitionOperator(
                        sinkParallelism, sink.getDataChangeEventHashFunctionProvider(sinkParallelism)),
                PartitioningEvent.class,
                sourceParallelism);
        pre.setUid(new OperatorUidGenerator("data-sync-pre-partition").generateUid(definitionId));
        OneInputTransformation<PartitioningEvent, Event> post = new OneInputTransformation<>(
                pre, "PostPartition", PostPartitionProcessor::new, Event.class, sinkParallelism);
        post.setInputPartitioning(StreamPartitioning.EVENT);
        post.setUid(new OperatorUidGenerator("data-sync-post-partition").generateUid(definitionId));
        return post;
    }
}
