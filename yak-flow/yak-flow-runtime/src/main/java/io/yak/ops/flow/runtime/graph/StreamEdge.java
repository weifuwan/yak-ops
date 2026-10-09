package io.yak.ops.flow.runtime.graph;

import io.yak.ops.core.api.operators.KeySelector;
import java.util.Objects;

/**
 * Directed edge of a StreamGraph with an explicit subtask partitioning strategy.
 *
 * <p>FORWARD requires matching upstream and downstream parallelism. REBALANCE distributes
 * records in round-robin order, and KEYED routes the same stable business key to the
 * same destination subtask.
 *
 * @param sourceId the graph-local upstream node ID
 * @param targetId the graph-local downstream node ID
 * @param partitioning the partition strategy for this edge
 * @param keySelector the key extractor, required only for KEYED partitioning
 */
public record StreamEdge(int sourceId, int targetId, StreamPartitioning partitioning, KeySelector<?> keySelector) {

    /** Creates a direct FORWARD edge for a matching-parallelism chain. */
    public StreamEdge(int sourceId, int targetId) {
        this(sourceId, targetId, StreamPartitioning.FORWARD);
    }

    public StreamEdge(int sourceId, int targetId, StreamPartitioning partitioning) {
        this(sourceId, targetId, partitioning, null);
    }

    /** Creates a keyed edge without erasing the selector's input-record type. */
    public static <T> StreamEdge keyed(int sourceId, int targetId, KeySelector<T> selector) {
        return new StreamEdge(sourceId, targetId, StreamPartitioning.KEYED, selector);
    }

    public StreamEdge {
        if (sourceId <= 0 || targetId <= 0) {
            throw new IllegalArgumentException("StreamEdge 的节点 ID 必须为正整数");
        }
        if (sourceId == targetId) {
            throw new IllegalArgumentException("StreamEdge 不允许节点直接连接自身");
        }
        Objects.requireNonNull(partitioning, "partitioning 不能为空");
        if ((partitioning == StreamPartitioning.KEYED) != (keySelector != null)) {
            throw new IllegalArgumentException("仅 KEYED 分区允许并必须提供 KeySelector");
        }
    }

    /** Omits the KeySelector to avoid leaking captured connection details in diagnostics. */
    @Override
    public String toString() {
        return "StreamEdge{sourceId=" + sourceId + ", targetId=" + targetId + ", partitioning=" + partitioning + "}";
    }
}
