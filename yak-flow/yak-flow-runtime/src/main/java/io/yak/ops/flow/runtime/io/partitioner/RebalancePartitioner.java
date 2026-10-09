package io.yak.ops.flow.runtime.io.partitioner;

/** Producer-local round-robin, starting from the producer index as in the prior RecordRouter. */
public final class RebalancePartitioner<T> implements StreamPartitioner<T> {

    private int nextTarget;

    public RebalancePartitioner(int upstreamSubtask, int downstreamParallelism) {
        if (upstreamSubtask < 0 || downstreamParallelism <= 0) {
            throw new IllegalArgumentException("Invalid REBALANCE partition setup");
        }
        this.nextTarget = upstreamSubtask % downstreamParallelism;
    }

    @Override
    public int selectChannel(T record, int numberOfChannels) {
        if (numberOfChannels <= 0 || nextTarget >= numberOfChannels) {
            throw new IllegalArgumentException("REBALANCE channel count changed");
        }
        int selected = nextTarget;
        nextTarget = (nextTarget + 1) % numberOfChannels;
        return selected;
    }
}
