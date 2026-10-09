# YakFlow Capability

Status: Active — Core / Runtime execution foundation; Connector integration pending

## Current State

旧 `yak-flow-connector-jdbc`、`yak-flow-connector-cdc-mysql` 与 Business 旧 execution 已删除。当前 Core 提供统一 Source/Sink 协议，YakFlow API 提供 Row/Schema/Logical Type 值对象，Runtime 提供单 JVM Streaming 执行骨架。**没有新的 JDBC / CDC Connector，不能进行实际跨库同步。**

## Execution Pipeline

```text
Transformation → StreamGraphGenerator → StreamGraph
    → StreamingJobGraphGenerator → JobGraph(JobVertex, JobEdge)
    → ExecutionGraph(ExecutionJobVertex, ExecutionVertex, Execution)
    → TaskDeployment → StreamTask / OperatorChain
```

- StreamGraph 保存逻辑节点与边；JobGraph 保存可部署的物理算子链和跨 Task 数据边。
- 无 Checkpoint、所有节点并行度 1 且 FORWARD 时，整个线性 Source → Operator* → Sink 作为单 JobVertex 链在同一 Mailbox 执行。其它合法图通过独立 JobVertex / ExecutionVertex 及有界 RecordChannel 执行。
- ExecutionGraph 管理 Job 状态、任务提交线程与取消；Execution 是单个 Subtask 的一次 Attempt，目前只支持首次尝试（0）。EmbeddedPipelineExecutor 只提交，EmbeddedJobClient 只代理状态、取消、结果与 Checkpoint。
- 当前拓扑只支持单 Source、任意个单输入 Operator、单 Sink 的严格线性链；FORWARD / REBALANCE / KEYED 保持已有分区行为。
- StreamTask 通过 TaskMailbox 顺序处理控制事件，由 MailboxDefaultAction 处理 Source/Channel 输入。NOTHING_AVAILABLE 暂停输入，不阻塞 Mail；Future 就绪或 Split 事件到来后恢复。正常 EOF、失败和取消的原有生命周期不变。

## Checkpoint Boundary

Source → Sink 支持 QuiescentCheckpointCoordinator 的单 JVM 静止切面：暂停 Source Split 分配和 Reader、等待 Channel 与在途写入排空、Sink flush(false)、FileCheckpointStore 原子持久化、通知 Source 完成。启用 Checkpoint 时禁用整个 Job 的内联链合并。文件名、CRC、版本化 Split/Enumerator 状态和 UID 指纹与旧 Runtime 保持兼容。语义是受限 at-least-once，不是 Flink Barrier Checkpoint 或 Exactly-once。

## Non-Goals

本阶段不实现 JDBC / MySQL CDC Connector、多个 Source/Sink、网络 Shuffle、Slot / RPC、自动 Retry / Failover、动态扩缩容、中间 Operator 状态恢复或完整分布式 Checkpoint。不能用 Runtime 单元测试或旧版 Release Evidence 宣称这些能力。

## Related

- [Core / Runtime Execution Contract](core-runtime-contract.md)
- [YakFlow Rules](../../../yak-flow/YAK_FLOW_RULES.md)
- [Data Sync Product](../data-sync/README.md)
- [Datasource JDBC Schema](../../../yak-ops-plugins/yak-ops-plugin-datasource/yak-ops-plugin-datasource-jdbc/src/main/java/io/yak/ops/plugin/database/jdbc/schema/)（仅用于元数据和 DDL 预览）
