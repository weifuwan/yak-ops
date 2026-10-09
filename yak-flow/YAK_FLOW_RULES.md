# YakFlow Rules

Status: Active

Scope: `yak-flow/yak-flow-api` 与 `yak-flow/yak-flow-runtime`。先遵循 [Architecture](../ARCHITECTURE.md)、[Core Rules](../yak-ops-core/CORE_RULES.md) 和 [Core / Runtime Contract](../docs/capabilities/yak-flow/core-runtime-contract.md)。

## Module Boundary

- Core 拥有 Source / Sink / Split / Transformation / Configuration / PipelineExecutor / JobClient 共享 API，不得依赖 Runtime。
- YakFlow API 只保留 YakRow / RowKind / YakTableSchema / YakDataType 等 JDK-only 值对象；旧 Source / Sink / Trace 协议和 JDBC / CDC Connector 已删除。
- Runtime 拥有逻辑 StreamGraph、物理 JobGraph、ExecutionGraph、StreamTask、SourceCoordinator、Channel 和 Checkpoint。运行期 Execution 是内存 Attempt，不是 DAO 的产品 Execution。
- Datasource JDBC Plugin 中的 Schema / DDL 方言只做元数据规划，不意味着 JDBC Connector 已经实现。

## Graph Compilation

- StreamGraphGenerator 负责逻辑图；StreamingJobGraphGenerator 负责拓扑、并行度、Checkpoint 能力校验，并生成含 JobVertex、JobEdge 的实际物理 JobGraph，不能以包装 StreamGraph 冒充编译。
- JobVertex 是可以部署的算子链。所有节点并行度为 1、边为 FORWARD 且不启用 Checkpoint 时，一个 Source → Operator* → Sink 链合并为一个 JobVertex；否则每个节点独立部署。
- JobGraph 冻结 JobID、已解析的执行模式与 Configuration；构图校验不得创建 SourceReader、SinkWriter 或工作线程。保持原有线性图/容量限制。

## Execution Lifecycle

- ExecutionJobVertex 拥有该物理 Vertex 的所有 ExecutionVertex；ExecutionVertex 是稳定 Subtask 位置；Execution 是一次实际尝试。当前仅创建 attempt 0，不实现热恢复或自动重试。
- ExecutionGraph 唯一维护 JobStatus、提交线程、取消与结果 Future；EmbeddedJobClient 只是查询/控制句柄，不能再维护第二套状态或 Worker。
- TaskDeployment 依据物理 JobVertex/JobEdge 创建 Task、Channel、OperatorChain、SourceCoordinator，并将每个 StreamTask 绑定到所属 Execution。它只负责装配、启动顺序、等待与资源清理，不成为另一层 Runner。
- 已删除 JobRunner、StreamJobRunner、CompiledJobPlan、JobExecution；不能为了测试注入重新建等价平行入口。
- 失败先中止 Channel、取消 Task，最终关闭 Task、Coordinator 和 Checkpoint Store。仅正常 END_OF_INPUT 时 finish Operator 和 flush(true) Sink。无界 Source 意外结束视为失败。

## Source / IO / Checkpoint

- SourceCoordinator 的事件循环与各 StreamTask Mailbox 分离。Split 事件处理确认与 Split 数据消费/Checkpoint 确认不能混为一谈；Reader 与 Operator 只在所属 Mailbox 执行。
- StreamTask 不再自建 Runnable 队列、固定控制 Mail 批量处理数量或 park 轮询。Mail 通过 TaskMailbox / MailboxExecutor 排队，只有 Task 线程可以执行；Future 只能在 Mail 的动作真正执行后确认。
- MailboxDefaultAction 每次处理一个 InputStatus 步骤；NOTHING_AVAILABLE 时等待 Source/Channel 的 isAvailable Future，暂停默认输入但继续执行控制 Mail。Future 就绪或新的 Split/NoMoreSplits 控制事件可恢复输入；持续报告「已就绪但无数据」应明确报错，不能忙轮询。
- TaskMailbox 状态 OPEN → QUIESCED → CLOSED；退出前拒绝新 Mail，关闭时未处理的控制 Future 必须异常完成。Task 取消和异步失败必须唤醒等待状态，正常 END_OF_INPUT 才执行最终 finish。
- RecordChannel 是有界多生产者、单消费者队列；FORWARD、REBALANCE、KEYED 行为保持原有逻辑。全部上游结束且队列清空才允许 EOF；失败/取消唤醒上下游。
- QuiescentCheckpointCoordinator 仍是单 JVM Source → Sink 静止切面：冻结分片、暂停 Reader、排空 Channel、Sink flush(false)、原子持久化，然后通知完成。
- 文件签名、CRC、Serializer 状态格式与恢复规则不变。未提供 Operator State 快照时不能在带中间算子的图上启用 Checkpoint。当前 at-least-once，不支持 Exactly-once。
- 当前 MailboxProcessor 仅用于本地单 JVM Task；参考 Flink 的 DefaultAction 暂停和控制 Mail 调度，不引入 Flink Mail 优先级系统、InputGate、ResultPartition、Barrier Checkpoint、RPC、Slot、JobMaster、动态扩缩容、KeyGroup 或自动 Failover。

## Verification

修改物理图和生命周期必须验证：JobVertex / JobEdge、单并行 Chaining、多并行 Subtask 和 Attempt 状态、失败/取消清理、SourceCoordinator、Checkpoint/Restore。没有真实 Connector 时不能声称 JDBC/CDC E2E 通过。
