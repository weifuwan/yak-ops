package io.yak.ops.flow.runtime.graph;

/** 本地 StreamEdge 的显式记录分区语义。 */
public enum StreamPartitioning {
    /** 按相同 Subtask Index 一对一连接；源和目标的并行度必须相同。 */
    FORWARD,

    /** 上游每个子任务独立按轮询把记录分发给所有目标子任务。 */
    REBALANCE,

    /** 通过下游输入记录的稳定业务键哈希分区；同键进入同一目标子任务。 */
    KEYED
}
