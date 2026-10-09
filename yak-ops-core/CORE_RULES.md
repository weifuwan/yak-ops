# Core Rules

Scope:
- `yak-ops-core/**`

Status:
- Active

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`

## Current Fact

`yak-ops-core` 持有批流共享的 Source / Sink / Split API、Collector / KeySelector、只读 `TaskInfo` 契约、类型化 Configuration、通用 `Transformation`，以及 `PipelineExecutor` / `JobClient` 协议。

`StreamGraph` / `StreamGraphGenerator`、Streaming Transformation、运行时 `OneInputOperator` 及工厂、`RuntimeTaskInfo`、`StreamTask`、`SourceCoordinator` 和 Checkpoint 实现均归 `yak-flow-runtime`。Core 不依赖 Runtime，也不保存执行中的线程或实例状态。

## Entry Rule

新能力只有同时满足以下条件之一时才考虑进入 Core：

- 有独立生命周期。
- 有稳定 Runtime Contract。
- 有独立状态机或并发资源边界。
- 明显独立于 Datasource 持久化和 HTTP。
- 未来存在真实多 owner 复用，而不是假设复用。

进入 Core 前必须先更新 `ARCHITECTURE.md` 并 Review ownership。

## Must Not

- 因为 Datasource 类变长就把代码搬进 Core。
- 因为“看起来通用”就提前抽 Core。
- 把 Repository / Mapper / HTTP / Controller 放进 Core。
- 把 Datasource 业务事实变成 Core runtime state。
- 重建已经删除的 Notification / Task / Alert runtime。

## Boundary

Core 拥有跨 Connector / Runtime 复用的稳定接口、配置和 Transformation 通用逻辑定义；运行图、Streaming Transformation、算子实例及执行机制全部归 Runtime。Core 不启动线程，也不持有 Job、Reader 调度或 Checkpoint 执行过程。

**代码始终留在真实 owner。** 运行期职责归 Runtime，数据库特定逻辑归 Connector，业务持久化和任务状态归 Business。

## Configuration / Graph Guardrails

- `Configuration` 只表达类型化运行策略和构图所需默认值，不承载 JobID、Subtask、Attempt、Reader 或 Coordinator 的活动状态。
- `Transformation` 只保存通用逻辑定义与可覆盖的算子属性；Runtime 的 `StreamGraph` 保存解析后的节点属性，不持有活动 Task、线程或连接。
- `CoreOptions.DEFAULT_PARALLELISM` 是默认并行度的唯一权威定义，`ExecutionOptions.DEFAULT_PARALLELISM` 只作为兼容别名；Checkpoint 间隔仅由 `CheckpointingOptions.CHECKPOINTING_INTERVAL` 定义，旧字段也只能指向同一个 `ConfigOption`。
- `StreamNode.getDeclaredParallelism()` 区分继承默认值与显式设置，`getParallelism()` 保存解析结果。构图/提交必须对继承默认值的节点做一致性检查，显式并行度不因提交配置变化而被覆盖。
- `StreamEdge` 携带 FORWARD / REBALANCE / KEYED 分区策略；并行度相同默认 FORWARD，不同默认 REBALANCE。KEYED 通过下游 Transformation 的 `keyBy(KeySelector)` 配置稳定非空业务键，不能使用数组或身份哈希；FORWARD 的两侧并行度不一致时必须拒绝。
- Runtime 的 `RuntimeOptions.CHANNEL_CAPACITY` 定义每个下游 Subtask 的队列容量，继续使用 `execution.local-channel.capacity` 配置键和原有默认值；Core 不再定义运行方式专属队列参数。
- Core 统一通过 `Sink.createWriter(WriterInitContext)` 创建每个 Subtask/Attempt 独立的 Writer；不再保留旧无参 `Sink.createWriter()` 或 `SinkV2`。`SinkWriter.write(T)` 与 `SinkWriter.write(T, Context)` 仍兼容。StatefulSinkWriter / SupportsWriterState 由 Runtime 的本地 Aligned Checkpoint 序列化、恢复；Core 只负责类型合同，不提供 StateBackend、事务 Committer 或 Exactly-once 保证。
- `PipelineOptions.MAX_PARALLELISM` 默认 128、上限 32768，构图时校验不能低于实际并行度；KEYED 分区用固定 KeyGroup ID 再映射 Subtask，不与部署并行度直接哈希。改变 maxParallelism 会改变 KeyGroup 身份，必须在恢复时检验指纹；当前没有 Keyed State Rescale。
- `CheckpointingOptions.STATE_DIRECTORY` / `RESTORE_LATEST` 只定义运行策略和恢复位置，实际版本化状态编码、目录独占锁、快照成功确认和 Task 暂停均归 `yak-flow-runtime`，不能在 Core 建立文件检查点执行器。
- 稳定算子 UID 与单次构图 ID 必须分清。Source / Sink / Operator 的公共契约只暴露 Connector 和 Runtime 真正共同需要的语义，不加入产品任务身份。
- Core 的 `TaskInfo` 只定义只读 Subtask/Parallelism/Attempt/KeyGroup maxParallelism 元信息；Runtime 的 `RuntimeTaskInfo` 提供 JobID/OperatorID 和诊断线程名称，`TaskEnvironment`、`OperatorCoordinatorContext`、`SourceReaderRuntimeContext` 均归 Runtime。Core 的 `SourceReaderContext#getConfiguration()` 返回独立的 `Configuration` 防御性副本；Task / Coordinator 身份不放入 Core。物理 Task 装配与中间 Operator 的状态 Checkpoint 属于当前 Runtime 实现，按 [Core / Runtime Execution Contract](../docs/capabilities/yak-flow/core-runtime-contract.md) 审查。

