package io.yak.ops.flow.runtime.graph;

import io.yak.ops.core.api.operators.KeySelector;
import java.util.Objects;

/**
 * StreamGraph 中的有向数据边，明确声明跨并行子任务的分区策略。
 *
 * <p>同一对节点最多有一条边。FORWARD 要求两侧并行度相同，REBALANCE 以轮询方式分发，
 * KEYED 通过稳定、非空的业务键决定目标 Subtask，保持同键记录进入同一写入序列。
 *
 * @param sourceId 上游节点构图 ID
 * @param targetId 下游节点构图 ID
 * @param partitioning 分区策略，不可隐式依赖运行时猜测
 * @param keySelector KEYED 策略必填，其他策略不允许设置
 */
public record StreamEdge(
        int sourceId, int targetId, StreamPartitioning partitioning, KeySelector<?> keySelector) {

    /** 向后兼容原有单并行链的 FORWARD 边。 */
    public StreamEdge(int sourceId, int targetId) {
        this(sourceId, targetId, StreamPartitioning.FORWARD);
    }

    public StreamEdge(int sourceId, int targetId, StreamPartitioning partitioning) {
        this(sourceId, targetId, partitioning, null);
    }

    /** 静态工厂保留 KeySelector 的源记录类型，不要求调用方进行泛型擦除转换。 */
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

    /** 避免默认 record.toString() 意外展开 KeySelector 内部捕获的连接参数。 */
    @Override
    public String toString() {
        return "StreamEdge{sourceId=" + sourceId + ", targetId=" + targetId
                + ", partitioning=" + partitioning + "}";
    }
}
