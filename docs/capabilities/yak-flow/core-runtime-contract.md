# Core / Runtime Execution Contract

Status: Active — embedded physical JobGraph and ExecutionGraph, with restricted Source/Sink checkpoint

Scope: `yak-ops-core` 与 `yak-flow/yak-flow-runtime`。仅定义 Core-based Runtime 的实际执行语义，历史发布版本不追溯更改。目前没有可运行的 JDBC / CDC Connector。

## Ownership

| Layer | Owner |
| --- | --- |
| Core | Source / Sink / Split、Transformation、Configuration / TaskInfo、PipelineExecutor / JobClient |
| Runtime Graph | StreamGraphGenerator、StreamGraph / StreamNode / StreamEdge、StreamingJobGraphGenerator |
| Runtime Physical Job | JobGraph / JobVertex / JobEdge；JobVertex 表示可部署算子链，JobEdge 只描述跨 Task 边 |
| Runtime Execution | ExecutionGraph / ExecutionJobVertex / ExecutionVertex / Execution；Job 状态和 Subtask / Attempt |
| Deployment | TaskDeployment 按物理图装配、启动、收口 StreamTask、RecordChannel、SourceCoordinator |
| Runtime Task / IO | StreamTask、OneInputStreamTask、SourceOperatorStreamTask、SinkOperatorStreamTask、OperatorChain、RecordChannel / RecordRouter |
| Runtime Checkpoint | QuiescentCheckpointCoordinator、CheckpointSnapshot、FileCheckpointStore |

Core 不能依赖 Runtime。Runtime 的 Execution 是当前 JVM 中一次 Subtask Attempt，不是 Data Sync 表中的业务 Execution/Attempt。

## StreamGraph → JobGraph

StreamingJobGraphGenerator 必须在创建 SourceReader、SinkWriter、工作线程前完成拓扑/类型/并行度和 Checkpoint 策略校验。JobGraph 拥有 JobID、执行模式、Configuration 快照、JobVertex / JobEdge；不能只把 StreamGraph 当作编译结果。原 StreamGraph 仅保留供稳定 Checkpoint 签名与 Boundedness 判断。

物理编译规则：当前只接受 Source → OneInputOperator* → Sink 的严格线性图。无 Checkpoint 且所有并行度均为 1、所有边均为 FORWARD 时，将整个链折叠成一个可部署 JobVertex（一个 Task Mailbox，没有跨 Task JobEdge）；其它图按节点生成 JobVertex，并在 JobEdge 上保存原 StreamEdge 的分区策略。Checkpoint 图不做算子链合并。

现有上限保持：单节点并行度 1–16、逻辑 Subtask 总量不超过 64、RecordChannel 容量 1–4096。配置键仍是 `execution.local-channel.capacity`，默认 64；Core 的默认并行度由 `parallelism.default` 管理。

## ExecutionGraph / Attempt

ExecutionJobVertex 聚合一个 JobVertex 的所有 ExecutionVertex；ExecutionVertex 表示固定 Subtask Index；Execution 表示该 Subtask 的单次 Attempt。每个 Execution 最多绑定和启动一次 StreamTask，当前只创建 attempt 0。Failure 后不会在原 Execution 上重启 Task；完整的 Task Failover / ExecutionVertex retry / Slot 调度暂未实现。

ExecutionGraph 是唯一 Job Status、取消、结果 Future 和提交 Worker 的 Owner；EmbeddedPipelineExecutor 负责编译并提交，EmbeddedJobClient 只代理 JobID、状态、取消、结果和可选 Checkpoint，不运行线程。TaskDeployment 负责资源的物理装配，不维护第二套 Job 状态。

TaskDeployment 依照物理 JobVertex 倒序装配 Sink/Operator/Source，先启动消费者再启动生产者。为每个目标 Subtask 创建有界 Channel，为每个物理 Execution 创建单独的 StreamTask 和 Source/Sink/Operator 运行实例。失败时中止 Channel、取消所有 Task，再尽力关闭 Task、Coordinator 与状态存储；取消 Future 在实际退出/清理后完成。

只有输入自然结束才依次调用 Operator.finish 和 SinkWriter.flush(true)；失败与取消时不能补充最终 Flush。无界 Source 未被取消却完成应视为异常。Task Mailbox/SourceCoordinator 现有线程和 Split 事件语义暂不改变。

## Transport

每个 RecordChannel 是多生产者、单消费者的有限队列，FORWARD 需要上下游相同并行度，REBALANCE 在目标 Channel 上轮询，KEYED 把业务键映射到目标 Subtask。同键分区不提供跨 Reader 全序，当前 KEYED 使用 hashCode/parallelism 而非 Flink KeyGroup，因此不支持有状态 Rescale。只有全部生产者结束且队列排空才能 EOF。

## Checkpoint / Recovery

只有 Source → Sink 支持 QuiescentCheckpointCoordinator 的单 JVM 静止切面：暂停 Enumerator / Reader → 快照 Split → 等待所有 Channel 排空及 Writer in-flight 结束 → Sink.flush(false) → FileCheckpointStore 持久化 → 通知 Source 完成。多输入与有状态中间算子的 Checkpoint 未实现。

保留旧 StreamGraph 的图签名、二进制状态文件格式、CRC、版本化 Serializer、原子替换与状态目录独占锁；新物理 JobGraph 不改变已有 Source → Sink 恢复协议。语义仅是受限 at-least-once，不能声称完整 Flink Barrier Checkpoint 或 Exactly-once。

## Flink References

- [StreamingJobGraphGenerator](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/api/graph/StreamingJobGraphGenerator.java)
- [DefaultExecutionGraph](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/DefaultExecutionGraph.java)
- [ExecutionVertex](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/ExecutionVertex.java)
- [StreamTask](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/StreamTask.java)

不为了匹配 Flink 名称而虚构 TaskManager、JobMaster、RPC、Slot、网络 Shuffle、自动故障恢复或完整 StateBackend；这些需要单独设计和验收。
