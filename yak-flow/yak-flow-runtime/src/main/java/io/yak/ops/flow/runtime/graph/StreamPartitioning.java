package io.yak.ops.flow.runtime.graph;

/** Record partitioning strategies supported by local StreamEdges. */
public enum StreamPartitioning {
    /** One-to-one routing by subtask index; both sides require equal parallelism. */
    FORWARD,

    /** Each producer independently distributes records round-robin to downstream subtasks. */
    REBALANCE,

    /** Routes records with an equal business key to the same downstream subtask. */
    KEYED,

    /** Routes a CDC PartitioningEvent to its already selected target subtask without rehashing. */
    EVENT
}
