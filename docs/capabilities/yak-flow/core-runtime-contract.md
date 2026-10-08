# Core / Runtime Execution Contract

Status: Proposed (architecture target; implementation incomplete)

Scope: `yak-ops-core` 与 `yak-flow/yak-flow-runtime` 的新批流执行链路。本文是后续实现必须遵守的设计边界，不表示新引擎已经实现或通过验收。

权威归属由 [Architecture](../../../ARCHITECTURE.md) 确定；现有产品能力由 [YakFlow Capability](README.md) 描述；实现规范分别见 [Core Rules](../../../yak-ops-core/CORE_RULES.md) 与 [YakFlow Rules](../../../yak-flow/YAK_FLOW_RULES.md)。

## Current Boundary

- `yak-ops-core` 已提供类型化 `Configuration`、`Transformation`、`StreamGraph`、Source / Sink / Operator 接口，以及 `PipelineExecutor` / `JobClient` 契约；它不执行任务。
- `yak-flow-runtime` 已有 `LocalPipelineExecutor`、`LocalJobClient`、`LocalJobRunner` 接口、`StreamTask` Mailbox、SourceOperator、SourceCoordinator、事件通道和协调侧局部 Checkpoint 类型。新执行链路尚未构成经过验证的完整 Source → Operator → Sink 运行闭环。
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
| 物理 Task 装配及数据通道 | Runtime | 仅对受支持拓扑装配；不向 Core 回写运行状态 |
| Subtask Index、实际并行度、Attempt、运行句柄 | Runtime 的 TaskInfo / TaskEnvironment（拟引入） | 一次 Task/Attempt 的运行事实，不是可任意覆写的配置项 |
| Enumerator、Reader 注册与 Split 投递 | Runtime 的 SourceCoordinator / SourceCoordinatorContext | 协调线程串行；分片生成逻辑留在 Connector |
| SourceReader 生命周期和数据轮询 | Runtime 的 SourceOperator / StreamTask | Task Mailbox 串行；不能创建第二条独立 Reader 控制线程 |
| Split 格式、游标/偏移、读取或写入实现 | Connector | 不向 Core/Runtime 泄漏 JDBC、Debezium 或产品 DTO |
| 产品任务发布、执行/重试及状态落库 | Data Sync | 不由 Runtime 创建第二套业务状态机 |

这里的 `TaskInfo`、`TaskEnvironment` 和编译后的执行输入是**目标边界**，不是现有类清单；新增类型必须由直接消费者和生命周期证明必要性，不能为了减少构造器参数创建万能 Context。

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
  CompiledJobPlan（拟引入，单次提交冻结）
             ↓
      LocalJobRunner
             ↓
 StreamTask(TaskEnvironment) ↔ SourceCoordinator(CoordinatorContext)
             ↓
  Source → Operator → Channel → Sink
```

- 默认值、显式值与运行时实际值是不同概念。`parallelism.default` 是配置；`StreamNode.parallelism` 是编译属性；Subtask 所属的实际并行度从 TaskInfo / 对应 Context 读取。
- `SourceCoordinator` 面向协调侧 Context 获取并行度和 Reader/Gateway 状态；`SourceOperator` 面向 Task 的运行上下文获取 Subtask 信息。二者不能因为字段名称相同而共享可变 Context。
- 允许多次基于同一逻辑定义提交 Job，但每次提交必须绑定自己的配置快照和运行身份。禁止构图时使用配置 A、提交时使用冲突的配置 B 却静默沿用部分旧属性。
- `CompiledJobPlan` 为目标设计名称；具体实现和归属必须在装配改造时确定，不能预先让 Core 持有本地 Task 工厂、线程、Channel 或 Connector 连接。
- Configuration 的可变容器可以用于构建；提交后依赖的配置视图必须固定或防御性复制。敏感连接配置不得进入日志、状态快照或 API 响应。
- Checkpoint 选项只能有一个明确权威来源；同一 key 的默认值与“未设置”语义不得因不同 ConfigOption 定义而不一致。

## Identity / Lifecycle

- `Transformation.id` 是单次构图标识，不等于跨作业恢复所需稳定 UID；自动生成的算子标识必须明确是否对拓扑变化稳定，不能把随机/递增 ID 当作持久化恢复凭据。
- `JobID` 标识一次 Runtime 作业提交；Operator UID 标识逻辑算子；Subtask Index 标识并行实例；Attempt 标识一次尝试；产品级 Execution / Attempt 是另一层身份。
- Task 生命周期由 Runtime 统一管理：初始化 → 状态恢复（如支持）→ 启动 → 处理输入/邮箱事件 → 正常结束或取消/失败 → 资源清理。不得在 Task 构造器打开连接或启动 Reader。
- Reader 注册、Split 投递及 `pollNext()` / Checkpoint 快照在各自所属的协调事件循环或 Task Mailbox 中按契约串行。`NoMoreSplits` 表示未来不再分配，不代表所有已分配 Split 读取结束。
- Reader 的 `isAvailable()` 应能唤醒输入循环，不能无数据时持续报告 ready；阻塞 I/O 不得长期占住 Task Mailbox；取消/失败要能够终止工作并释放资源。
- 网关投递成功、Task Mailbox 处理完成、Reader 消费完成、Checkpoint 持久化成功，是不同确认级别；不得混为一个成功状态。

## Dataflow / Checkpoint Safety

- Graph 的每条边必须有明确的数据路由语义；跨不同算子并行度时，不能仅凭 `StreamEdge(sourceId,targetId)` 推断正确的记录分发。Channel 需限制容量并能传播背压、结束、取消和失败。
- Source 的 Split 数量不等于 Reader 并行度；当日后需要 keyed routing 时，CDC 同一主键的变更顺序必须受分区和写入规则约束，不能盲目 Rebalance。
- 当前新 Runtime 仅有 Source 侧局部快照/分配跟踪。目标 Checkpoint 需要由上层统一协调 Enumerator、Reader、数据通道和 Sink 的同一边界，并持久化完成后才能通知成功。
- SourceCoordinatorCheckpoint、`SplitAssignmentTracker` 的历史记录不能单独证明跨进程恢复。恢复需要版本化序列化、Reader 与 Enumerator 状态对齐、失败分片归还或重分配，以及完成确认规则。
- `SinkWriter.flush` 或 Split 事件处理成功，不等于 Exactly-once 事务提交证明。新 Runtime 在全链路恢复验收前不得声称具备 at-least-once / exactly-once 等端到端保证；旧 YakFlow 的具体保证仍以其原有契约为准。

## Migration / Compatibility

- 新旧 API 在提交入口显式区分；既有产品的数据同步能力和 Connector 调用路径保持原有行为，迁移时才按直接消费者增加必要的兼容适配。
- 不同时维护两份含义相近的 `Configuration`、Gateway 或 Coordinator 角色；已有 `SourceReaderContext` / `SplitEnumeratorContext` 由 Core 声明、Runtime 实现，避免新的跨层依赖倒置。
- 先校验 Core 泛型、构图、重复 ConfigOption 和旧测试的静态一致性；再迁移 Task Context、Source 协调、作业装配、Channel 和 Checkpoint。每阶段的完成证据留在对应 PR，不写入本契约。
- 后续实现应提供默认/显式并行度、配置快照隔离、重复/失败 Split、Mailbox 控制、取消清理、Checkpoint 成败及恢复的测试。真实数据库验收继续使用现有 [Backend Acceptance](../../../.github/workflows/backend-acceptance.yml) 路径。

## Flink Reference / Non-Goals

设计借鉴 Flink 的 `CoreOptions.DEFAULT_PARALLELISM`、`StreamGraphGenerator`、`StreamTask(Environment)`、`SourceOperator`、`SourceCoordinatorContext` 和 `SplitAssignmentTracker` 的职责分离，不逐字移植代码或继承其全部运行保证。

- [Flink CoreOptions](https://github.com/apache/flink/blob/master/flink-core/src/main/java/org/apache/flink/configuration/CoreOptions.java)
- [Flink StreamTask](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/runtime/tasks/StreamTask.java)
- [Flink SourceOperator](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/streaming/api/operators/SourceOperator.java)
- [Flink SourceCoordinatorContext](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/source/coordinator/SourceCoordinatorContext.java)

不在本阶段引入分布式 JobManager/TaskManager、远程 RPC、Slot/网络 Shuffle、完整 Watermark 系统、动态扩缩容或端到端 Exactly-once，也不改写产品级任务与重试边界。
