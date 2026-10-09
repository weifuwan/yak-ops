package io.yak.ops.flow.runtime.io;

import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.operators.KeySelector;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.io.partition.ResultPartition;
import io.yak.ops.flow.runtime.io.partitioner.ForwardPartitioner;
import io.yak.ops.flow.runtime.io.partitioner.KeyedPartitioner;
import io.yak.ops.flow.runtime.io.partitioner.RebalancePartitioner;
import io.yak.ops.flow.runtime.io.partitioner.StreamPartitioner;
import java.util.Objects;

/**
 * StreamTask's output adapter. Chooses a target with a StreamPartitioner, then sends the record to
 * its producer-owned ResultPartition. No consumer polling or checkpoint state lives here.
 */
public final class RecordWriterOutput<T> implements ReaderOutput<T> {

    private final ResultPartition<T> partition;
    private final StreamPartitioner<T> partitioner;
    private boolean finished;

    public RecordWriterOutput(
            StreamEdge edge, int upstreamSubtask, int upstreamParallelism, ResultPartition<T> partition) {
        this(edge, upstreamSubtask, upstreamParallelism, partition, 128);
    }

    public RecordWriterOutput(
            StreamEdge edge, int upstreamSubtask, int upstreamParallelism,
            ResultPartition<T> partition, int maxParallelism) {
        Objects.requireNonNull(edge, "edge");
        this.partition = Objects.requireNonNull(partition, "partition");
        if (upstreamParallelism <= 0 || upstreamSubtask < 0 || upstreamSubtask >= upstreamParallelism
                || partition.getProducerIndex() != upstreamSubtask) {
            throw new IllegalArgumentException("Invalid producer identity");
        }
        int channels = partition.getNumberOfSubpartitions();
        this.partitioner = switch (edge.partitioning()) {
            case FORWARD -> new ForwardPartitioner<>(upstreamSubtask, upstreamParallelism, channels);
            case REBALANCE -> new RebalancePartitioner<>(upstreamSubtask, channels);
            case KEYED -> new KeyedPartitioner<>(keySelector(edge), maxParallelism);
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> KeySelector<T> keySelector(StreamEdge edge) {
        return (KeySelector<T>) edge.keySelector();
    }

    @Override
    public void collect(T record) throws Exception {
        if (finished) {
            throw new IllegalStateException("RecordWriterOutput finished");
        }
        Objects.requireNonNull(record, "record");
        int channel = partitioner.selectChannel(record, partition.getNumberOfSubpartitions());
        partition.emitRecord(channel, record);
    }

    /** Ordered checkpoint barrier bypasses partitioning, reaching every consumer subtask. */
    public void broadcastBarrier(long checkpointId) {
        if (finished) {
            throw new IllegalStateException("RecordWriterOutput finished");
        }
        partition.broadcastBarrier(checkpointId);
    }

    public void finish() {
        if (finished) {
            throw new IllegalStateException("RecordWriterOutput finished twice");
        }
        partition.finish();
        finished = true;
    }
}
