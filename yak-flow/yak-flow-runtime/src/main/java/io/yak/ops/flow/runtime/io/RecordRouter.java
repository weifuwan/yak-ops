package io.yak.ops.flow.runtime.io;

import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.operators.KeySelector;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import java.util.List;
import java.util.Objects;

/** 单个上游子任务的数据出口，将记录按 StreamEdge 的明确分区策略发送到下游 Channel。 */
public final class RecordRouter<T> implements ReaderOutput<T> {

    private final StreamEdge edge;
    private final int upstreamIndex;
    private final List<RecordChannel<T>> channels;
    private int nextTarget;
    private boolean finished;

    public RecordRouter(StreamEdge edge, int upstreamIndex, int upstreamParallelism,
            List<RecordChannel<T>> channels) {
        this.edge = Objects.requireNonNull(edge, "edge 不能为空");
        Objects.requireNonNull(channels, "channels 不能为空");
        if (upstreamIndex < 0 || upstreamIndex >= upstreamParallelism || channels.isEmpty()) {
            throw new IllegalArgumentException("记录分区的子任务或并行度无效");
        }
        this.channels = List.copyOf(channels);
        if (edge.partitioning() == StreamPartitioning.FORWARD
                && upstreamParallelism != channels.size()) {
            throw new IllegalArgumentException("FORWARD 需要上下游并行度相同");
        }
        this.upstreamIndex = upstreamIndex;
        this.nextTarget = upstreamIndex % channels.size();
    }

    @Override
    public void collect(T record) throws Exception {
        if (finished) {
            throw new IllegalStateException("结果分区已经结束");
        }
        Objects.requireNonNull(record, "发送记录不能为空");
        int target = switch (edge.partitioning()) {
            case FORWARD -> upstreamIndex;
            case REBALANCE -> {
                int selected = nextTarget;
                nextTarget = (nextTarget + 1) % channels.size();
                yield selected;
            }
            case KEYED -> keyPartition(record);
        };
        channels.get(target).send(record);
    }

    @SuppressWarnings("unchecked")
    private int keyPartition(T record) throws Exception {
        KeySelector<T> selector = (KeySelector<T>) edge.keySelector();
        Object key = Objects.requireNonNull(selector.getKey(record), "KEYED 路由不能使用 null 键");
        if (key.getClass().isArray()) {
            throw new IllegalArgumentException("KEYED 不允许数组键（默认数组哈希与业务键不一致）");
        }
        return Math.floorMod(key.hashCode(), channels.size());
    }

    /** 只有上游 Task 正常结束时调用；每个目标 Channel 都必须感知生产者结束。 */
    public void finish() {
        if (finished) {
            throw new IllegalStateException("结果分区不能重复结束");
        }
        finished = true;
        for (RecordChannel<T> channel : channels) {
            channel.producerFinished(upstreamIndex);
        }
    }
}
