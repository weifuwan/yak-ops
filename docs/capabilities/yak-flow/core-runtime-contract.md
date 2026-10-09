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
| Deployment | TaskDeployment 按物理图装配、启动、收口 StreamTask、ResultPartition、InputGate、SourceCoordinator |
| Runtime Task / IO | StreamTask、StreamTaskSourceInput / StreamTaskNetworkInput、OneInputStreamTask、SourceOperatorStreamTask、SinkOperatorStreamTask、OperatorChain；TaskMailbox / MailboxExecutor / MailboxDefaultAction / MailboxProcessor |
| Runtime Transport | RecordWriterOutput、StreamPartitioner、ResultPartition / ResultSubpartition、InputGate |
| Runtime Checkpoint | QuiescentCheckpointCoordinator、CheckpointSnapshot、FileCheckpointStore |

Core 不能依赖 Runtime。Runtime 的 Execution 是当前 JVM 中一次 Subtask Attempt，不是 Data Sync 表中的业务 Execution/Attempt。

## StreamGraph → JobGraph

StreamingJobGraphGenerator 必须在创建 SourceReader、SinkWriter、工作线程前完成拓扑/类型/并行度和 Checkpoint 策略校验。JobGraph 拥有 JobID、执行模式、Configuration 快照、JobVertex / JobEdge；不能只把 StreamGraph 当作编译结果。原 StreamGraph 仅保留供稳定 Checkpoint 签名与 Boundedness 判断。

物理编译规则：当前只接受 Source → OneInputOperator* → Sink 的严格线性图。无 Checkpoint 且所有并行度均为 1、所有边均为 FORWARD 时，将整个链折叠成一个可部署 JobVertex（一个 Task Mailbox，没有跨 Task JobEdge）；其它图按节点生成 JobVertex，并在 JobEdge 上保存原 StreamEdge 的分区策略。Checkpoint 图不做算子链合并。

现有上限保持：单节点并行度 1–16、逻辑 Subtask 总量不超过 64、InputGate 每目标缓存总容量 1–4096。配置键仍是 `execution.local-channel.capacity`，默认 64；Core 的默认并行度由 `parallelism.default` 管理。

## ExecutionGraph / Attempt

ExecutionJobVertex 聚合一个 JobVertex 的所有 ExecutionVertex；ExecutionVertex 表示固定 Subtask Index；Execution 表示该 Subtask 的单次 Attempt。每个 Execution 最多绑定和启动一次 StreamTask，当前只创建 attempt 0。Failure 后不会在原 Execution 上重启 Task；完整的 Task Failover / ExecutionVertex retry / Slot 调度暂未实现。

ExecutionGraph 是唯一 Job Status、取消、结果 Future 和提交 Worker 的 Owner；EmbeddedPipelineExecutor 负责编译并提交，EmbeddedJobClient 只代理 JobID、状态、取消、结果和可选 Checkpoint，不运行线程。TaskDeployment 负责资源的物理装配，不维护第二套 Job 状态。

TaskDeployment 依照物理 JobVertex 倒序装配 Sink/Operator/Source，先启动消费者再启动生产者。每个目标 Subtask 拥有独立 InputGate，每个上游 Task 拥有 ResultPartition 并向所有目标 Gate 注册 ResultSubpartition；物理 Execution 的 Source/Sink/Operator 实例保持隔离。失败先中止所有 InputGate，再取消 Task 并最终关闭 Task、Coordinator 与 Checkpoint 存储；Cancellation Future 在资源清理后完成。

只有输入自然结束才依次调用 Operator.finish 和 SinkWriter.flush(true)；失败与取消时不能补充最终 Flush。无界 Source 未被取消却完成应视为异常。StreamTask 使用 MailboxProcessor 默认动作和独立 TaskMailbox；SourceCoordinator 事件循环与 Split 交付/确认协议保持不变。

## StreamTask Mailbox

每个 StreamTask 在一个虚拟线程上运行独立的 MailboxProcessor。TaskMailbox 允许别的线程投递 Mail，但只有所属 Task 线程可以消费它。MailboxExecutor 的 Future 必须在控制动作真正执行后成功完成；执行失败会让所属 Task 失败。

输入处理是 MailboxDefaultAction，每次只执行一步 processInput()，并与控制 Mail 交替进行。NOTHING_AVAILABLE 且 isAvailable() 未完成时只暂停默认输入动作，Mailbox 仍可处理 Coordinator/Checkpoint 控制消息；Future 就绪后恢复输入。Source 收到 AddSplit/NoMoreSplits 事件时也显式重新激活默认动作，避免不主动完成旧 availability Future 的 Reader 一直挂起。连续返回「就绪但无数据」是 Source 协议问题，不能 busy-spin。

StreamTask 负责 openTask → runMailboxLoop → 自然 END_OF_INPUT 时 finishTask → closeTask。异常/取消不执行最终 finish。退出时先 QUIESCE，再 CLOSED，未执行 Mail 的 Future 必须异常完成。取消中断 Task 工作线程以退出阻塞调用，完成清理后才完成 cancellation Future。

本阶段不实现 Flink 的 Mail 优先级/Batch、Watermark 或远程网络 Shuffle；保留 SourceOperator 与现有 QuiescentCheckpoint 文件协议。
## Transport

- `StreamTaskInput<T>` 统一 SourceOperator 和物理 Gate 的非阻塞输入接口；`StreamTaskSourceInput` 调用 Reader，`StreamTaskNetworkInput` 调用下游 `InputGate`，并将 getAvailableFuture 交给 Task Mailbox。当前不支持网络 InputGate 或多输入算子。
- 每个上游 Subtask 拥有一个 `ResultPartition`，为各目标 Task 创建一条 `ResultSubpartition`；目标 `InputGate` 注册其所有上游 Subpartition，以 round-robin 轮询非空输入，避免持续偏向同一上游。
- 一个 InputGate 的 `capacity` 是**全部上游 Subpartition 共享**的总排队上限，不随并行度放大；队列满使用可中断 Condition 等待，消费后通知生产者，无 25ms 轮询。
- `RecordWriterOutput` 通过独立 `StreamPartitioner` 选择目标。FORWARD 同并行度，一条生产者流对应一条下游输入；REBALANCE 按生产者独立轮询；KEYED 保留当前 `Math.floorMod(key.hashCode(), downstreamParallelism)`，**不是 Flink KeyGroup，不能宣称支持状态 Rescale**。
- 全部生产者结束并排空对应 Gate 的缓冲后才返回 END_OF_INPUT。失败/取消唤醒阻塞的发送者和等待消费者；Checkpoint 的 `drainedFuture` 不仅等待队列为空，还要等待正在执行的下游 Writer 回调结束。

## Checkpoint / Recovery

只有 Source → Sink 支持 QuiescentCheckpointCoordinator 的单 JVM 静止切面：暂停 Enumerator / Reader → 快照 Split → 等待所有 InputGate 排空及 Writer in-flight 结束 → Sink.flush(false) → FileCheckpointStore 持久化 → 通知 Source 完成。多输入与有状态中间算子的 Checkpoint 未实现。

保留旧 StreamGraph 的图签名、二进制状态文件格式、CRC、版本化 Serializer、原子替换与状态目录独占锁；新物理 JobGraph 不改变已有 Source → Sink 恢复协议。语义仅是受限 at-least-once，不能声称完整 Flink Barrier Checkpoint 或 Exactly-once。

## Flink References

- [StreamingJobGraphGenerator](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/api/graph/StreamingJobGraphGenerator.java)
- [DefaultExecutionGraph](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/DefaultExecutionGraph.java)
- [ExecutionVertex](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/ExecutionVertex.java)
- [StreamTask](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/StreamTask.java)
- [MailboxProcessor](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/mailbox/MailboxProcessor.java)
- [TaskMailbox](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/mailbox/TaskMailbox.java)

不为了匹配 Flink 名称而虚构 TaskManager、JobMaster、RPC、Slot、网络 Shuffle、自动故障恢复或完整 StateBackend；这些需要单独设计和验收。
