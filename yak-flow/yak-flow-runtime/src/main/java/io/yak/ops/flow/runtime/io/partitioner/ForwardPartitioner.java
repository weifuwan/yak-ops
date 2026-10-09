package io.yak.ops.flow.runtime.io.partitioner;

/** One-to-one forwarding from upstream subtask i to downstream subtask i. */
public final class ForwardPartitioner<T> implements StreamPartitioner<T> {

    private final int sourceSubtask;

    public ForwardPartitioner(int sourceSubtask, int upstreamParallelism, int downstreamParallelism) {
        if (upstreamParallelism <= 0 || downstreamParallelism != upstreamParallelism
                || sourceSubtask < 0 || sourceSubtask >= upstreamParallelism) {
            throw new IllegalArgumentException("FORWARD requires equal parallelism and a valid producer");
        }
        this.sourceSubtask = sourceSubtask;
    }

    @Override
    public int selectChannel(T record, int numberOfChannels) {
        if (sourceSubtask >= numberOfChannels) {
            throw new IllegalArgumentException("FORWARD channel count changed");
        }
        return sourceSubtask;
    }
}
