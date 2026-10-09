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
- 无 Checkpoint、所有节点并行度 1 且 FORWARD 时，整个线性 Source → Operator* → Sink 作为单 JobVertex 链在同一 Mailbox 执行。其它合法图通过各自的 ExecutionVertex 与本地 ResultPartition / InputGate 传输。
- ExecutionGraph 管理 Job 状态、任务提交线程与取消；Execution 是单个 Subtask 的一次 Attempt，默认只运行首次尝试（0）。显式设置 execution.restart.max-attempts（默认 0）且存在完整可用的磁盘 Checkpoint 时，失败会停止全部 Task，重新创建 Attempt 并从已完成 Split/Enumerator 状态恢复整个本地 Job；无快照或状态损坏不会自动全量重放。EmbeddedPipelineExecutor 只提交，EmbeddedJobClient 代理状态、取消、结果与 Checkpoint。
- 当前拓扑只支持单 Source、任意个单输入 Operator、单 Sink 的严格线性链；FORWARD / REBALANCE / KEYED 保持已有分区行为。
- StreamTask 的控制事件经过 TaskMailbox 串行处理，输入统一经 StreamTaskInput；MailboxDefaultAction 在 NOTHING_AVAILABLE 时挂起，不阻塞 Mail，Future 就绪或 Split 事件到来再读取。

## Local StreamTask Input and Physical Partitions

`StreamTaskSourceInput` 和 `StreamTaskNetworkInput` 实现相同的非阻塞 `StreamTaskInput`。跨 JobVertex 的每个上游 StreamTask 使用 `RecordWriterOutput` 与独立 `StreamPartitioner` 写入其 `ResultPartition`；每个下游 Task 通过 `InputGate` 消费所有生产者的 `ResultSubpartition`。输入 Gate 提供轮询公平性、统一队列容量、完成通知和可中断背压，而不是以前的混合 RecordChannel / RecordRouter。

FORWARD / REBALANCE / KEYED 路由保持兼容。KEYED 目前仍使用 Java hashCode 而不是 Flink KeyGroup；本地 InputGate 不是 Flink RemoteInputChannel，也不实现网络 Credit/Barrier。

## Source Coordination

AddSplitEvent 使用 Connector 提供的 SimpleVersionedSerializer 生成版本化独立字节，Task Mailbox 接收后重新反序列化。Reader 与 Enumerator 使用 Core SourceEvent 和 Runtime SourceEventWrapper 双向传递自定义事件；Coordinator 校验 Job/Operator/Subtask/Attempt，旧 Attempt 的事件会被拒绝。全 Job 从已完成的 Checkpoint 恢复时合并 Reader Split 进度与 Coordinator 分片历史。当前不支持在原 Coordinator 内局部热替换 Reader。

## Checkpoint Boundary

Source → Sink 支持 QuiescentCheckpointCoordinator 的单 JVM 静止切面：暂停 Source Split 分配和 Reader、等待 InputGate 缓冲与在途写入排空、Sink flush(false)、FileCheckpointStore 原子持久化、通知 Source 完成。启用 Checkpoint 时禁用整个 Job 的内联链合并。文件名、CRC、版本化 Split/Enumerator 状态和 UID 指纹与旧 Runtime 保持兼容。语义是受限 at-least-once，不是 Flink Barrier Checkpoint 或 Exactly-once。

## Non-Goals

本阶段不实现 JDBC / MySQL CDC Connector、多个 Source/Sink、网络 Shuffle、Slot / RPC、局部 Reader-only Failover、动态扩缩容、中间 Operator 状态恢复或完整分布式 Checkpoint。仅新增受 Checkpoint 约束的显式本地整 Job Attempt 恢复。不能用 Runtime 单元测试或旧版 Release Evidence 宣称这些能力。

## Related

- [Core / Runtime Execution Contract](core-runtime-contract.md)
- [YakFlow Rules](../../../yak-flow/YAK_FLOW_RULES.md)
- [Data Sync Product](../data-sync/README.md)
- [Datasource JDBC Schema](../../../yak-ops-plugins/yak-ops-plugin-datasource/yak-ops-plugin-datasource-jdbc/src/main/java/io/yak/ops/plugin/database/jdbc/schema/)（仅用于元数据和 DDL 预览）
