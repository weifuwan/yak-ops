# Core / Runtime Execution Contract

Status: Active (local linear runtime + Source/Sink checkpoint implemented; general operator state/recovery proposed)

Scope: `yak-ops-core` 与 `yak-flow/yak-flow-runtime` 的新批流执行链路。线性 Source → Sink 已具备单节点持久化 Checkpoint 及恢复代码；多源/分叉和有状态中间 Operator 恢复仍未实现。代码存在不代表编译、CI 或真实数据库 E2E 已通过。

权威归属由 [Architecture](../../../ARCHITECTURE.md) 确定；现有产品能力由 [YakFlow Capability](README.md) 描述；实现规范分别见 [Core Rules](../../../yak-ops-core/CORE_RULES.md) 与 [YakFlow Rules](../../../yak-flow/YAK_FLOW_RULES.md)。

## Current Boundary

- `yak-ops-core` 已提供类型化 `Configuration`、`Transformation`、`StreamGraph`、Source / Sink / Operator 接口，以及 `PipelineExecutor` / `JobClient` 契约；它不执行任务。
- `yak-flow-runtime` 已有 `CompiledJobPlan`、Task/Coordinator/Reader Context、`LocalPipelineExecutor` / `LocalJobClient`、`LocalStreamJobRunner` / `LocalTaskGraph`、Source/Operator/Sink StreamTask、`LocalChannel` / `LocalResultPartition`、`LocalCheckpointCoordinator` / `FileCheckpointStore`。线性图允许多子任务，Source → Sink 支持受限持久化恢复；中间 Operator 尚无状态快照协议，多源、分叉、动态扩缩容与 Exactly-once 均不属于当前实现保证。
- `yak-flow-api` 的旧 Source / Sink、`CheckpointState` 与现有 Connector / 产品调用方仍是过渡范围。新旧 API 不能在同一执行路径中隐式混用，也不能用旧路径的验收证明新 Runtime 可用。
- 产品 `Data Sync` 持有 Task / Route / Execution / Attempt、Retry、Schedule 和业务恢复状态。Runtime 的 `JobID`、Task 身份及局部 Checkpoint 不替代产品持久化标识。
- 当前目标是单 JVM 本地运行。分布式资源管理、RPC、Slot、JobManager / TaskManager 不是本契约的要求。

## Ownership

| 事实或职责 | Owner | 边界 |
| --- | --- | --- |
| 默认并行度、运行模式、Checkpoint 策略 | Core 的类型化 Configuration | 声明用户/系统策略；不保存 Job、Subtask、Attempt 运行状态 |
| 显式算子并行度、上游依赖、稳定 UID | Core 的 Transformation | 逻辑定义；不打开连接、不创建线程 |
| 节点实际并行度、边、类型和有界性 | Core 的 StreamGraph | 经构图解析的定义快照；不是活动执行实例 |
| 运行模式与图/配置的一致性、Job 提交 | Runtime 的 PipelineExecutor 实现 | 冻结提交配置，建立一次提交对应的不可变执行输入 |
| 物理 Task 装配及数据通道 | Runtime | 单并行 FORWARD 图采用内联链；并行线性图采用独立 Task、下游有界 Channel 和显式分区策略 |
| Subtask Index、实际并行度、Attempt、运行上下文 | Runtime 的 TaskInfo / TaskEnvironment | TaskInfo 保存不可变身份与实际并行度；TaskEnvironment 提供配置防御性副本和只读取消信号 |
| Enumerator、Reader 注册与 Split 投递 | Runtime 的 SourceCoordinator / SourceCoordinatorContext / OperatorCoordinatorContext | 协调上下文提供 Job、Operator 与实际并行度，事件循环串行调度；分片生成逻辑留在 Connector |
| SourceReader 生命周期和数据轮询 | Runtime 的 SourceOperator / StreamTask | Task Mailbox 串行；不能创建第二条独立 Reader 控制线程 |
| Split 格式、游标/偏移、读取或写入实现 | Connector | 不向 Core/Runtime 泄漏 JDBC、Debezium 或产品 DTO |
| 产品任务发布、执行/重试及状态落库 | Data Sync | 不由 Runtime 创建第二套业务状态机 |

`TaskInfo` / `TaskEnvironment` 已有最小实现，但没有引入通用 Gateway / Coordinator / Connector 生命周期持有者。运行环境不因缩短构造器参数就成为万能 Context；Coordination Context 与物理 Task 装配仍需直接消费者和生命周期证明。

## Configuration → Graph → Runtime

`Configuration` 保存默认策略；`Transformation` 上的显式并行度覆盖默认值；`StreamGraphGenerator` 只在构图边界解析一次。`StreamNode.getParallelism()` 表达节点已解析的并行度，不应让运行中的 Operator 再读取 `parallelism.default` 重算。

目标数据流：

```text
Configuration + Transformation
             ↓
      StreamGraphGenerator
             ↓
  StreamGraph（逻辑图/节点属性）
             ↓
  CompiledJobPlan（JobID / 模式 / 配置快照）
             ↓
      LocalJobRunner
             ↓
 StreamTask(TaskEnvironment) ↔ SourceCoordinator(OperatorCoordinatorContext)
             ↓
  Source Task → LocalResultPartition → LocalChannel → Operator Task* → Sink Task
```

- 默认值、显式值与运行时实际值是不同概念。`parallelism.default` 是配置；`StreamNode.parallelism` 是编译属性；Subtask 所属的实际并行度从 TaskInfo / 对应 Context 读取。
- `SourceOperator` 只持有 Core `SourceReaderContext` 和 Reader 生命周期；`SourceReaderRuntimeContext` 通过 `TaskEnvironment.taskInfo()` 获取 Subtask Index / Attempt / 实际并行度，提供独立配置快照并通过 OperatorEventGateway 发送异步 Split 请求。
- `SourceCoordinator` 通过 `OperatorCoordinatorContext` 持有 Job / Operator 身份与已解析并行度，`SourceCoordinatorContext` 只在事件线程管理 Reader/Gateway、分片交付与 Enumerator。Coordinator 与 Reader Context 不共享可变状态，也不从 `parallelism.default` 重算已确定的并行度。
- 注册及请求 Split 时按 Job、Operator、Subtask、Attempt 校验，重复注册或不同 Attempt 的原地替换必须明确拒绝。当前 Runtime 仍不支持单 Reader 局部重启；Attempt 校验不构成动态恢复能力。
- 允许多次基于同一逻辑定义提交 Job，但每次提交必须绑定自己的配置快照和运行身份。禁止构图时使用配置 A、提交时使用冲突的配置 B 却静默沿用部分旧属性。
- `CompiledJobPlan` 归 Runtime，每次编译生成独立 JobID，并绑定 StreamGraph、已解析运行模式与配置防御性快照。JobClient 和 TaskInfo 必须复用该 JobID；继承默认并行度的节点在提交配置不一致时拒绝执行。Core 不持有本地 Task 工厂、线程、Channel 或 Connector 连接。
- Configuration 的可变容器可以用于构建；提交后依赖的配置视图必须固定或防御性复制。敏感连接配置不得进入日志、状态快照或 API 响应。
- `CoreOptions.DEFAULT_PARALLELISM` 和 `CheckpointingOptions.CHECKPOINTING_INTERVAL` 分别是默认并行度与周期 Checkpoint 间隔的权威定义。Checkpoint 未显式设置时读取为 `Duration.ZERO`（禁用），`getOptional` 仍表示未设置；旧 `ExecutionOptions` 字段仅作为同一 ConfigOption 的源码别名。配置可被校验不等于 Runtime 已经实现周期 Checkpoint 调度。

## Current Local Job Assembly

内置 `LocalPipelineExecutor()` 在提交线程调用 `LocalStreamJobRunner.validate(plan)`，不启动任务就拒绝不支持的拓扑。当前范围：

- 恰好一个 Source、一个 Sink，中间为零个或多个单输入 Operator；拓扑严格线性，不支持分叉、多输入和多源。
- 每个 Source/Operator/Sink 节点实际并行度为 1～16，单 Job 子任务总数不超过 64。
- Source → Sink（不含中间 Operator）可以启用周期或手动 Checkpoint，但必须配置独占状态目录及所有节点的稳定 UID；含中间 Operator 的图因缺少状态恢复协议仍不得启用。

所有节点并行度为 1 且边为 FORWARD 时，沿用一个 Mailbox 内的 `LocalOperatorChain`。其它线性图由 `LocalTaskGraph` 为每个子任务装配独立的 Reader、Operator 或 SinkWriter，并先启动下游消费者、再启动上游生产者。每次 Job 的 Task / Channel / Coordinator 互相隔离。

StreamEdge 具有明确分区语义：

- **FORWARD**：源/目标并行度必须相同，按相同 Subtask Index 一对一传递。
- **REBALANCE**：上游每个 Subtask 独立轮询所有下游子任务；并行度不同时为 GraphGenerator 的默认策略，不保证主键保序。
- **KEYED**：下游 OneInputTransformation 或 SinkTransformation 通过 `keyBy(KeySelector)` 声明稳定业务键；将相同键哈希到同一目标子任务。来源仍需保证同键事件自身顺序，跨 Reader 到达顺序不受此策略保证，亦不构成 exactly-once。

`LocalChannel` 对每个目标 Subtask 创建一个多生产者、单消费者的有界队列，`CoreOptions.LOCAL_CHANNEL_CAPACITY` 默认 64 条，最大 4096 条。生产者在队列满时阻塞虚拟 Task 线程形成背压；所有上游完成且队列排空后，下游才收到 EOF。任一 Task 或 SourceCoordinator 失败，Runtime 统一中止 Channel 并取消其它 Task。

只有正常 EOF 才调用每级 Operator 的 `finish`，并在全部上游生产者结束后执行 Sink 的 `flush(true)`。异常、取消或部分初始化失败只关闭已创建资源，不发送成功结束标记。一个 Operator 或 SinkWriter 只由所属 Task Mailbox 串行访问；记录通过同步 Collector 转发，不由 Runtime 隐式复制或异步缓存。

## Current Checkpoint Coordination and Recovery

Source → Sink 路径（无中间 OneInputOperator，允许并行 Source Reader 与多个 SinkWriter）通过 `LocalCheckpointCoordinator` 完成单 JVM 对齐式静止切面：

1. `SourceCoordinator.pauseForCheckpoint()` 暂停新 Split 请求和 Enumerator 后台发现结果回调，先等待在途 AddSplit/NoMoreSplits 事件被 Reader Mailbox 确认；不允许单 Reader Attempt 热替换。
2. 各 `SourceOperatorStreamTask` 在 Mailbox 内执行 `SourceReader.snapshotState()` 并暂停下一次 pollNext；此时 Source 不再向 Channel 生产新记录。
3. 上游到下游依次等待所有 `LocalChannel.drainedFuture()`，包括已经出队但仍处在 SinkWriter.write 中的记录。随后在每个 Sink Mailbox 中执行 `SinkWriter.flush(false)`，不能把此操作当成 exactly-once 事务提交。
4. `FileCheckpointStore` 使用 Connector 的 `SimpleVersionedSerializer` 写出 Enumerator、Reader Split 进度及 Coordinator Assignment History，以 CRC 校验并同目录原子替换发布已完成状态。发布后才调用 `SourceCoordinator.notifyCheckpointComplete` 及各 Reader 的 `notifyCheckpointComplete`；失败或取消则中止 Checkpoint 并恢复轮询/释放资源。
5. 启用 `CheckpointingOptions.RESTORE_LATEST` 时，从状态目录最新的完整快照解析 Enumerator 与各 Reader Split 状态，按稳定 UID/节点并行度/路由指纹校验后创建新的 Job。Reader 保存的最新 Split 进度覆盖协调侧同 splitId 的旧分配历史；无法确定是否完成的分片可能重放，语义为 **at-least-once，不保证 Exactly-once**。

配置和限制：`CHECKPOINTING_INTERVAL` 大于零时周期触发；`LocalJobClient.checkpoint()` 可在运行时手动触发；`STATE_DIRECTORY` 必须显式设置、独占且允许原子替换；恢复使用 `RESTORE_LATEST`。当前只允许 1 个在途 Checkpoint。没有稳定 UID、状态已损坏、并行度/拓扑不匹配或缺失状态文件时必须明确失败，不能静默退回重新全量运行。没有中间 Operator 的快照不表示任意 Operator/Connector 都具备恢复语义；Connector 需要提供正确的 Split / EnumeratorState serializer 和外部偏移提交行为。

旧 `io.yak.ops.flow.runtime` 根包中的 `LocalExecutionEngine` / `LocalExecution` 及其消息、Checkpoint、状态/指标类型仍被 Data Sync、JDBC 和 MySQL CDC 的旧 Source/Sink 协议直接使用。两套 API 的 `poll` / `pollNext`、Sink 批量写入 / 单记录写入与状态确认契约不同；只有旧 Connector 与所有调用方迁移到 Core-based Runtime 并完成验收后才能删除旧类。本阶段不通过重命名或移动包来假装已完成协议迁移。

## Identity / Lifecycle

- `Transformation.id` 是单次构图标识，不等于跨作业恢复所需稳定 UID；自动生成的算子标识必须明确是否对拓扑变化稳定，不能把随机/递增 ID 当作持久化恢复凭据。
- `TaskInfo` 包含一次 Runtime JobID、当前图内 operatorId、subtaskIndex、该 Operator 的实际 parallelism 和 attemptNumber；构造时必须验证范围。Operator UID 才是逻辑算子的稳定恢复标识，图内 operatorId 不承诺跨版本稳定；产品级 Execution / Attempt 是另一层身份。
- `TaskEnvironment` 拥有每个 Task 独立的 Configuration 防御性快照。外部配置修改不能改变任务已创建时的视图；取消信号只用于查询，由所属 `StreamTask.cancelAsync()` 驱动，不允许 Connector 借此越权控制生命周期。
- Task 生命周期由 Runtime 统一管理：初始化 → 状态恢复（如支持）→ 启动 → 处理输入/邮箱事件 → 正常结束或取消/失败 → 资源清理。不得在 Task 构造器打开连接或启动 Reader。
- Reader 注册、Split 投递及 `pollNext()` / Checkpoint 快照在各自所属的协调事件循环或 Task Mailbox 中按契约串行。事件请求携带 Subtask / Attempt 身份；对旧 Attempt 的消息拒绝处理。`NoMoreSplits` 必须排在已分配 Split 的 Mailbox 确认之后，它表示未来不再分配，不代表所有已分配 Split 读取结束。
- Reader 的 `isAvailable()` 应能唤醒输入循环，不能无数据时持续报告 ready；阻塞 I/O 不得长期占住 Task Mailbox；取消/失败要能够终止工作并释放资源。
- 网关投递成功、Task Mailbox 处理完成、Reader 消费完成、Checkpoint 持久化成功，是不同确认级别；不得混为一个成功状态。

## Dataflow / Checkpoint Safety

- StreamEdge 明确保存 FORWARD / REBALANCE / KEYED 和必要的 KeySelector；FORWARD 两侧并行度不一致时拒绝。LocalChannel 限制容量并传播背压；全部上游完成且队列排空后才 EOF，失败和取消要通知全部 Channel 与 Task。
- Source 的 Split 数量不等于 Reader 并行度；CDC 必须使用稳定主键选择 KEYED 才能保证同键进入同一目标 Subtask。多个 Reader 对同键同时发送事件仍可能交错，必须由 Source / Connector 保证该键的事件序列，不能盲目使用 REBALANCE。
- Source → Sink 已提供本地静止切面 CheckpointCoordinator，协调 Enumerator / Reader、Channel 排空和 Sink flush(false) 并原子持久化。含中间 Operator 的状态快照、跨进程分布式恢复和事务性 Exactly-once 仍未实现。
- `SourceCoordinatorCheckpoint` 与 `SplitAssignmentTracker` 的历史记录仍不能单独证明全链路 Checkpoint 成功；恢复必须结合 Reader Split 快照、版本化序列化、状态目录原子发布和下游 Flush。已确认不了完成状态的分片可能重放。
- `SinkWriter.flush(false)` 与状态目录原子发布组合，只支持受限 Source → Sink 的 at-least-once 快照恢复；崩溃时可能重放写入，不能宣称 Exactly-once。真实数据库场景尚需 Connector 级 E2E 验证；旧 YakFlow 的保证仍以其原有契约为准。

## Migration / Compatibility

- 新旧 API 在提交入口显式区分；既有产品的数据同步能力和 Connector 调用路径保持原有行为，迁移时才按直接消费者增加必要的兼容适配。
- Core 的 `SourceReaderContext` / `SplitEnumeratorContext` 由 Runtime 实现；`SourceReaderRuntimeContext#getConfiguration` 必须返回 Task 配置的防御性副本。上下行事件分别使用 `OperatorEventGateway`（Reader → Coordinator）与 `SubtaskGateway`（Coordinator → Reader）；方向不同，不引入第三套等价 Gateway。
- 每次迁移同时确认 Core 泛型、构图、配置唯一性、旧 Runtime 兼容与测试；Task / Coordinator Context、线性多 Task、Channel 与 Source/Sink 的 Checkpoint 协调及持久化恢复已编码。中间 Operator 状态持久化、多源/分叉、局部 Reader 热恢复和分布式 Checkpoint 仍未实现，不能把旧 Checkpoint 接口自动视为已迁移。每次完成证据留在对应 PR，不写入本契约。
- 后续实现应提供默认/显式并行度、配置快照隔离、重复/失败 Split、Mailbox 控制、取消清理、Checkpoint 成败及恢复的测试。真实数据库验收继续使用现有 [Backend Acceptance](../../../.github/workflows/backend-acceptance.yml) 路径。

## Flink Reference / Non-Goals

设计借鉴 Flink 的 `CoreOptions.DEFAULT_PARALLELISM`、`StreamGraphGenerator`、`StreamTask(Environment)`、`SourceOperator`、`SourceCoordinatorContext` 和 `SplitAssignmentTracker` 的职责分离，不逐字移植代码或继承其全部运行保证。

- [Flink CoreOptions](https://github.com/apache/flink/blob/master/flink-core/src/main/java/org/apache/flink/configuration/CoreOptions.java)
- [Flink StreamTask](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/StreamTask.java)
- [Flink SourceOperator](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/api/operators/SourceOperator.java)
- [Flink SourceCoordinatorContext](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/source/coordinator/SourceCoordinatorContext.java)

不在本阶段引入分布式 JobManager/TaskManager、远程 RPC、Slot/网络 Shuffle、完整 Watermark 系统、动态扩缩容或端到端 Exactly-once，也不改写产品级任务与重试边界。
