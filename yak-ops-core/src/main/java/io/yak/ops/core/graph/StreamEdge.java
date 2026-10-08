package io.yak.ops.core.graph;

/**
 * StreamGraph 中从上游节点到下游节点的一条有向数据流边。
 *
 * <p>当前仅支持每对节点一条连接，不携带分区策略、输入端口或 Side Output。
 * 后续引入多输入算子时，再扩展 Edge 的输入端口与分区语义。
 *
 * @param sourceId 上游节点的构图 ID
 * @param targetId 下游节点的构图 ID
 * @author weifuwan
 */
public record StreamEdge(int sourceId, int targetId) {

    public StreamEdge {
        if (sourceId <= 0 || targetId <= 0) {
            throw new IllegalArgumentException("StreamEdge 的节点 ID 必须为正整数");
        }
        if (sourceId == targetId) {
            throw new IllegalArgumentException("StreamEdge 不允许节点直接连接自身");
        }
    }
}
