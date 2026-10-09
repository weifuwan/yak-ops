# Core / Runtime Execution Contract

Status: Active — embedded physical JobGraph and ExecutionGraph, with restricted Source/Sink checkpoint

Scope: `yak-ops-core` 与 `yak-flow/yak-flow-runtime`。仅定义 Core-based Runtime 的实际执行语义，历史发布版本不追溯更改。已有 JDBC Source Connector 的独立 bounded 读取实现；没有产品级 JDBC Sink / MySQL CDC 的跨库同步。

## Ownership

| Layer | Owner |
| --- | --- |
| Core | Source / Sink / SinkWriter / StatefulSinkWriter、WriterInitContext、Split / Transformation、Configuration / TaskInfo、PipelineExecutor / JobClient |
| Runtime Graph | StreamGraphGenerator、StreamGraph / StreamNode / StreamEdge、StreamingJobGraphGenerator |
| Runtime Physical Job | JobGraph / JobVertex / JobEdge；JobVertex 表示可部署算子链，JobEdge 只描述跨 Task 边 |
| Runtime Execution | ExecutionGraph / ExecutionJobVertex / ExecutionVertex / Execution；Job 状态和 Subtask / Attempt |
| Deployment | TaskDeployment 按物理图装配、启动、收口 StreamTask、ResultPartition、InputGate、SourceCoordinator |
| Runtime Task / IO | StreamTask、StreamTaskSourceInput / StreamTaskNetworkInput、OneInputStreamTask（包含 Sink）、SourceOperatorStreamTask、StreamOperator / OneInputStreamOperator / SinkWriterOperator、OperatorChain；TaskMailbox / MailboxExecutor / MailboxDefaultAction / MailboxProcessor |
| Runtime Transport | RecordWriterOutput、StreamPartitioner、ResultPartition / ResultSubpartition、InputGate |
| Runtime Checkpoint | AlignedCheckpointCoordinator、CheckpointBarrier、CheckpointSnapshot、OperatorStateBackend、FileCheckpointStore |

Core 不能依赖 Runtime。Runtime 的 Execution 是当前 JVM 中一次 Subtask Attempt，不是 Data Sync 表中的业务 Execution/Attempt。

## StreamGraph → JobGraph

StreamingJobGraphGenerator 必须在创建 SourceReader、SinkWriter、工作线程前完成拓扑/类型/并行度和 Checkpoint 策略校验。JobGraph 拥有 JobID、执行模式、Configuration 快照、JobVertex / JobEdge；不能只把 StreamGraph 当作编译结果。原 StreamGraph 仅保留供稳定 Checkpoint 签名与 Boundedness 判断。

物理编译规则：当前只接受 Source → OneInputStreamOperator* → Sink 的严格线性图。无 Checkpoint 且所有并行度均为 1、所有边均为 FORWARD 时，将整个链折叠成一个可部署 JobVertex（一个 Task Mailbox，没有跨 Task JobEdge）；其它图按节点生成 JobVertex，并在 JobEdge 上保存原 StreamEdge 的分区策略。Checkpoint 图不做算子链合并。

现有本地执行限制保持：单节点实际并行度 1–16、逻辑 Subtask 总量最多 64、InputGate 每目标缓存总容量 1–4096；KeyGroup 最大并行度通过 pipeline.max-parallelism 配置（默认 128，上限 32768），不得小于实际并行度。配置键仍是 `execution.local-channel.capacity`，默认 64；Core 的默认并行度由 `parallelism.default` 管理。

## ExecutionGraph / Attempt

ExecutionJobVertex 聚合一个 JobVertex 的所有 ExecutionVertex；ExecutionVertex 表示固定 Subtask Index；Execution 表示一次具体 Attempt。正常首次 Attempt 为 0。仅当执行配置显式设置 execution.restart.max-attempts（默认 0）且存在有效完成的本地 Source → Sink Checkpoint 时，ExecutionGraph 可以终止全部旧 Task/Coordinator/Writer/Channel，依序创建全体新的 Execution（Attempt +1），从磁盘 Reader Split + Enumerator State 恢复整个 Job。旧 StreamTask 不允许重复绑定或重新启动。**不支持局部 Reader-only Failover、无状态全量盲重启、Slot 调度或远程 Task 部署。**

ExecutionGraph 是唯一 Job Status、取消、结果 Future 和提交 Worker 的 Owner；EmbeddedPipelineExecutor 负责编译并提交，EmbeddedJobClient 只代理 JobID、状态、取消、结果和可选 Checkpoint，不运行线程。TaskDeployment 负责资源的物理装配，不维护第二套 Job 状态。

TaskDeployment 依照物理 JobVertex 倒序装配 Sink/Operator/Source，先启动消费者再启动生产者。每个目标 Subtask 拥有独立 InputGate，每个上游 Task 拥有 ResultPartition 并向所有目标 Gate 注册 ResultSubpartition；物理 Execution 的 Source/Sink/Operator 实例保持隔离。失败先中止所有 InputGate，再取消 Task 并最终关闭 Task、Coordinator 与 Checkpoint 存储；Cancellation Future 在资源清理后完成。

只有输入自然结束才依次调用 Operator.finish 和 SinkWriter.flush(true)；失败与取消时不能补充最终 Flush。无界 Source 未被取消却完成应视为异常。StreamTask 使用 MailboxProcessor 默认动作和独立 TaskMailbox；SourceCoordinator 事件循环与 Split 交付/确认协议保持不变。

## StreamTask Mailbox

每个 StreamTask 在一个虚拟线程上运行独立的 MailboxProcessor。TaskMailbox 允许别的线程投递 Mail，但只有所属 Task 线程可以消费它。MailboxExecutor 的 Future 必须在控制动作真正执行后成功完成；执行失败会让所属 Task 失败。

输入处理是 MailboxDefaultAction，每次只执行一步 processInput()，并与控制 Mail 交替进行。NOTHING_AVAILABLE 且 isAvailable() 未完成时只暂停默认输入动作，Mailbox 仍可处理 Coordinator/Checkpoint 控制消息；Future 就绪后恢复输入。Source 收到 AddSplit/NoMoreSplits 事件时也显式重新激活默认动作，避免不主动完成旧 availability Future 的 Reader 一直挂起。连续返回「就绪但无数据」是 Source 协议问题，不能 busy-spin。

StreamTask 负责 openTask → runMailboxLoop → 自然 END_OF_INPUT 时 finishTask → closeTask。异常/取消不执行最终 finish。退出时先 QUIESCE，再 CLOSED，未执行 Mail 的 Future 必须异常完成。取消中断 Task 工作线程以退出阻塞调用，完成清理后才完成 cancellation Future。

本阶段不实现 Flink 的 Mail 优先级/Batch、Watermark 或远程网络 Shuffle；SourceOperator 继续由独立 Mailbox 驱动，支持本地有序 Barrier。
## Transport

- `StreamTaskInput<T>` 统一 SourceOperator 和物理 Gate 的非阻塞输入接口；`StreamTaskSourceInput` 调用 Reader，`StreamTaskNetworkInput` 调用下游 `InputGate`，并将 getAvailableFuture 交给 Task Mailbox。当前不支持网络 InputGate 或多输入算子。
- 每个上游 Subtask 拥有一个 `ResultPartition`，为各目标 Task 创建一条 `ResultSubpartition`；目标 `InputGate` 注册其所有上游 Subpartition，以 round-robin 轮询非空输入，避免持续偏向同一上游。
- 一个 InputGate 的 `capacity` 是**全部上游 Subpartition 共享**的总排队上限，不随并行度放大；队列满使用可中断 Condition 等待，消费后通知生产者，无 25ms 轮询。
- `RecordWriterOutput` 通过独立 `StreamPartitioner` 选择目标。FORWARD 同并行度，一条生产者流对应一条下游输入；REBALANCE 按生产者独立轮询；KEYED 用 MurmurHash(key.hashCode()) 分配到固定 KeyGroup，再按 `keyGroup * parallelism / maxParallelism` 映射到 Subtask。KeyGroup ID 不因并行度变化；OperatorStateBackend 可持久化归属当前 Subtask 的 Keyed State，恢复时按逻辑状态名校验 Key Serializer 版本，不兼容就拒绝而非静默返回空；**不支持 Serializer Migration 或状态 Rescale**。
- 全部生产者结束并排空对应 Gate 的缓冲后才返回 END_OF_INPUT。失败/取消唤醒阻塞的发送者和等待消费者；Checkpoint 不再调用全阶段 drainedFuture，单输入 Barrier 对齐与 Task Mailbox 状态 ACK 才是快照屏障；drainedFuture 仍可供独立通道观测。

## StreamOperator and Sink

`StreamOperator` 定义统一 open、正常结束 finish、close 生命周期；`OneInputStreamOperator` 支持单输入与同步 Collector。`OneInputOperatorFactory` 直接创建 `OneInputStreamOperator`；独立 `OneInputStreamTask` 同样运行 `SinkWriterOperator`，不再拥有独立 `SinkOperatorStreamTask`。Chained OperatorChain 和独立 Sink 使用相同 SinkWriterOperator 的 Writer 创建、write、flush 和关闭路径。

Core `Sink.createWriter(WriterInitContext)` 获取 Sink 节点的 Job/Operator/Subtask/Attempt/maxParallelism 及隔离配置；旧的无参 `Sink.createWriter()` 与 `SinkV2` 已移除，`SinkWriter.write(T, Context)` 是唯一写入方法。新 `SinkWriter.Context` 提供 timestamp=null、watermark=Long.MIN_VALUE（没有时间流语义前不伪造），并作为真实 write 接口调用。实现 SupportsWriterState 的 Sink 可通过版本化 Serializer 保存 StatefulSinkWriter 快照并恢复；缺失恢复合同的 StatefulSinkWriter 在启用 Checkpoint 时继续拒绝。不实现事务 Committer。

## Source Coordination and Event Contracts

- Core 的 SourceEvent 是 Connector 自定义事件标记；SourceReaderContext.sendSourceEventToCoordinator 与 SplitEnumeratorContext.sendEventToSourceReader 使用 Runtime SourceEventWrapper 实际路由。当前同 JVM 发送的是 SourceEvent 对象，**还没有跨进程的 SourceEvent 序列化/RPC**；需要分布式时应先明确版本协议。
- AddSplitEvent 使用 Source.getSplitSerializer() 生成版本号与独立 byte[]，Task Mailbox 只用自己的 Source Split Serializer 反序列化。SourceCoordinator 保留 Job/Operator/Subtask/Attempt 身份检查，迟到旧 Attempt 的请求不得修改当前 Enumerator。
- Coordinator 记录 SplitAssignmentTracker 的 Checkpoint 分配快照，并在恢复时和 Reader 的最新 Split 进度一起恢复。AddSplit/NoMoreSplits 的 Mailbox ACK 不等于记录消费或持久化。Reader 失败时**全 Job 退出并重新部署**，不在旧 Coordinator 中热替换 Gateway，避免上游重试和未回滚下游混用。

## Checkpoint / Recovery

支持 Source → OneInputStreamOperator* → Sink 的单 JVM 对齐 Barrier：冻结 Enumerator / Reader → Snapshot Reader 并按 FIFO 注入 Barrier → **在 Reader / Enumerator 恢复执行前，将所有 Source 状态序列化为不可变字节** → Source 恢复 → Gate 对各生产者对齐 Barrier → Task Mailbox 快照 Operator/Writer State 并 ACK → FileCheckpointStore 持久化 → 通知 Source 完成。Barrier 后 Producer 先等待目标 Gate 完成对齐，不能占满共享输入缓存；Producer 提前正常 EOF 时撤销 Checkpoint、保留普通数据流。多输入与跨网络 Barrier 不支持。

非 KEYED 拓扑签名保留；KEYED 签名仍绑定 Murmur3 和 maxParallelism。无中间状态的 Source/Sink 快照继续写 v1，读写均支持原版；存在 Operator/Writer State 时写 v2（按稳定 UID + subtask + 状态名）。CRC、Serializer 版本、原子替换、目录锁不变。只保证本地 at-least-once，不支持 Exactly-once。

## Flink References

- [StreamingJobGraphGenerator](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/api/graph/StreamingJobGraphGenerator.java)
- [DefaultExecutionGraph](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/DefaultExecutionGraph.java)
- [ExecutionVertex](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/ExecutionVertex.java)
- [StreamTask](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/StreamTask.java)
- [SinkWriterOperator](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/operators/sink/SinkWriterOperator.java)
- [KeyGroupRangeAssignment](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/state/KeyGroupRangeAssignment.java)
- [MailboxProcessor](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/mailbox/MailboxProcessor.java)
- [TaskMailbox](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/mailbox/TaskMailbox.java)

不为了匹配 Flink 名称而虚构 TaskManager、JobMaster、RPC、Slot、网络 Shuffle、局部 Reader Failover 或远程/增量 StateBackend；当前是单 JVM 内存 OperatorStateBackend + 持久化状态文件，固定并行度整 Job 恢复，可能重放已写出的行，只有 at-least-once。
