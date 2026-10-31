package io.yak.ops.flow.runtime.graph;

import io.yak.ops.core.api.operators.KeySelector;
import java.util.Objects;

/**
 * Directed edge of a StreamGraph with an explicit subtask partitioning strategy.
 *
 * <p>FORWARD requires matching upstream and downstream parallelism. REBALANCE distributes
 * records in round-robin order, and KEYED routes the same stable business key to the
 * same destination subtask. EVENT uses the destination selected by a CDC pre-partition operator.
 * StreamGraph validates edge endpoints and owns their uniqueness.
 */
public final class StreamEdge {

    private final int sourceId;
    private final int targetId;
    private final StreamPartitioning partitioning;
    private final KeySelector<?> keySelector;

    /** Creates a direct FORWARD edge for a matching-parallelism chain. */
    public StreamEdge(int sourceId, int targetId) {
        this(sourceId, targetId, StreamPartitioning.FORWARD);
    }

    /** Creates a directed edge using the requested unkeyed partition strategy. */
    public StreamEdge(int sourceId, int targetId, StreamPartitioning partitioning) {
        this(sourceId, targetId, partitioning, null);
    }

    /**
     * Creates a directed edge, with a KeySelector only for KEYED partitioning.
     *
     * @param sourceId graph-local upstream node ID
     * @param targetId graph-local downstream node ID
     * @param partitioning destination partitioning strategy
     * @param keySelector input key extractor, required only for KEYED partitioning
     */
    public StreamEdge(int sourceId, int targetId, StreamPartitioning partitioning, KeySelector<?> keySelector) {
        if (sourceId <= 0 || targetId <= 0) {
            throw new IllegalArgumentException("StreamEdge node IDs must be positive");
        }
        if (sourceId == targetId) {
            throw new IllegalArgumentException("StreamEdge must not connect a node to itself");
        }
        Objects.requireNonNull(partitioning, "partitioning must not be null");
        if ((partitioning == StreamPartitioning.KEYED) != (keySelector != null)) {
            throw new IllegalArgumentException("KeySelector is required only for KEYED partitioning");
        }
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.partitioning = partitioning;
        this.keySelector = keySelector;
    }

    /** Creates a keyed edge without erasing the selector's input-record type. */
    public static <T> StreamEdge keyed(int sourceId, int targetId, KeySelector<T> selector) {
        return new StreamEdge(sourceId, targetId, StreamPartitioning.KEYED, selector);
    }

    /** Returns the graph-local upstream node ID. */
    public int sourceId() {
        return sourceId;
    }

    /** Returns the graph-local downstream node ID. */
    public int targetId() {
        return targetId;
    }

    /** Returns the routing strategy associated with this edge. */
    public StreamPartitioning partitioning() {
        return partitioning;
    }

    /** Returns the keyed-routing extractor, or null for unkeyed edges. */
    public KeySelector<?> keySelector() {
        return keySelector;
    }

    /** Omits the KeySelector to avoid leaking captured connection details in diagnostics. */
    @Override
    public String toString() {
        return "StreamEdge{sourceId=" + sourceId + ", targetId=" + targetId + ", partitioning=" + partitioning + "}";
    }
}
