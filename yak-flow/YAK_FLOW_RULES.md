# YakFlow Rules

Status: Active

Scope: `yak-ops-core`、`yak-flow/yak-flow-runtime`、`yak-flow/yak-flow-connector-base`、`yak-flow/yak-flow-connector-jdbc` 与 `yak-flow/yak-flow-connector-cdc-mysql`。先遵循 [Architecture](../ARCHITECTURE.md)、[Core Rules](../yak-ops-core/CORE_RULES.md) 和 [Core / Runtime Contract](../docs/capabilities/yak-flow/core-runtime-contract.md)。

## JavaDoc and Comments

Core, Runtime, Connector Base, and JDBC Connector all follow the same
[YakFlow Javadoc and Comment Convention](../docs/capabilities/yak-flow/core-runtime-comment-rules.md).
Use English comments, document public SPI method behavior and meaningful database, mailbox,
checkpoint and resource-lifecycle guarantees, and avoid restating method names. The existing
Backend Quality step checks all four modules for the objective rules; method semantics stay
subject to code review.

## Module Boundary

- Core 拥有 Source / Sink / Split / Transformation / Configuration / PipelineExecutor / JobClient 共享 API，不得依赖 Runtime。
- Core owns the Source / Sink API and database-neutral RowData / TableRecord, TableId and LogicalType / TableSchema. The yak-flow-api module is removed; do not recreate duplicate row or schema contracts.
- Runtime 拥有 StreamGraph / JobGraph / ExecutionGraph、StreamTask / StreamTaskInput、SourceCoordinator、ResultPartition / InputGate 和 Checkpoint。运行期 Execution 是内存 Attempt，不是产品 Execution。
- Connector Base 只依赖 Core，提供非阻塞 SourceReader 消费层、有界 Future 队列与阻塞 SplitFetcher，以及 Sink 同步批量触发合同；不得依赖 Runtime、Datasource、JDBC 或产品 Task。`BatchingSinkWriterBase` 不能另建一份缓冲；`BatchOutput` 是记录的唯一缓冲 owner。
- JDBC Source 独立实现 Core Source API，使用 Connector Base 的异步 Reader 和 Connection 级隔离；SQL 方言只属于 JDBC Connector。The JDBC Connector owns Catalog/Dialect/Converter contracts and native SQL/DDL. Use the shared JdbcFactory SPI for vendor discovery; put concrete dialect, catalog, converter implementations under internal/{dialect,catalog,convert}. The existing Datasource Catalog is transitional and must remain untouched until a later adapter migration; do not add another Datasource dialect.
- JDBC Sink 基于 Connector Base 的 BatchOutput / Mailbox Trigger：`JdbcWriter` 在 `sink/writer`；`JdbcOutputFormat` 在 JDBC `internal` 内持有单 Writer 连接与事务；`internal/executor` 下的泛型 TableBufferedStatementExecutor 是唯一记录缓存，TableChangelogStatementExecutor 管理多表预编译 Statement 和同一事务内的有序批次，执行 Flush 期间只借用 Before 引用。JdbcWriter 不自建缓存、计时线程或重试执行器。
- JDBC Sink 支持多个 source TableId → target TableId 的显式表路由、目标列顺序映射及匹配的 LogicalType Root；APPEND 仅接收 INSERT。UPSERT 接收 INSERT、独立 UPDATE_AFTER、DELETE，以及同一 Sink Subtask 上相邻且同表的 UPDATE_BEFORE / UPDATE_AFTER 成对事件；主键相同仅 UPSERT，主键变化时才 DELETE 旧键并 UPSERT 新键。Before/After 不完整或交错时拒绝执行，不静默提交，不声明并行 Writer 全局顺序。后续 CDC 接线必须保证事件成对连续、同表与主键稳定归属同一 Sink Subtask（可复用 Runtime KEYED），不得在 JDBC 内重建分区器。
- 自动 BatchSize / Mailbox 定时 Flush 在 Before/After 尚未完整时暂缓；只有完成一对更新后才能自动提交。Checkpoint 仍由 Runtime 串行调用 flush(false)，正常结束才 flush(true)；显式 Checkpoint/结束 Flush 遇到不完整 Before 必须 fail closed，不得确认丢失 Before 的快照。显式成功 Flush 才提交，关闭只回滚/释放资源；失败/取消不得重试不确定的 JDBC 提交或隐式 Flush。跨库验收覆盖 MySQL→PostgreSQL、PostgreSQL→Oracle、Oracle→MySQL，但不等于产品 E2E。仍为 at-least-once，无 XA/Committer/Exactly-once。OVERWRITE/TRUNCATE 不在重启恢复路径自动执行，后续须独立定义安全合同。
- MySQL CDC Connector 单独持有 Debezium Binlog I/O、Offset 和版本化 Split/Enumerator 状态；复用 Core Source、Connector Base Reader/Fetcher 与 Runtime Checkpoint，不复制通用 SourceReaderBase 或 Executor。PR1 仅支持从当前位点启动的纯 Binlog 增量与完成 Checkpoint 的恢复；不将内存中的 Debezium 预取 Offset 直接作为已输出进度，不默默从最新位点回退。MySQL Schema History 必须纳入恢复状态，Unsupported DDL/Schema Evolution 必须明确失败；不声明 Exactly-once。
- MySQL Hybrid Snapshot 使用独立于 PR1 stream-only 的 Source/Split/Enumerator 状态合同；只允许单列非空 BIGINT 主键，使用 keyset Chunk 边界，Snapshot Split 按成功投递的主键恢复。Enumerator 保留待分配/已分配/已完成的状态，Reader 完成但未收到 ACK 的 Split 在快照中继续保留。Binlog Low Watermark 必须先于任何 Snapshot 规划，Snapshot 完成后捕获 High Watermark，只有完成覆盖全部 Snapshot 输出的 Checkpoint 才允许 Binlog 从 Low 开始重放。首版采用全局重放修正 Snapshot 并发变更（at-least-once），**不是 Flink CDC 逐 Chunk 的 L/H Watermark 归并算法**。Hybrid Source 必须启用周期持久化 Checkpoint，拒绝不匹配的恢复定义。
- JDBC Source 的一个定义管理多张表；Enumerator 逐表异步发现并分配 Split。单整数主键采用不重叠的区间和已输出主键恢复，其他表采用整 Split 重放语义；只保证受限 at-least-once，不承诺变化中数据库的全局一致性快照。

## Graph Compilation

- StreamGraphGenerator 负责逻辑图；StreamingJobGraphGenerator 负责拓扑、并行度、Checkpoint 能力校验，并生成含 JobVertex、JobEdge 的实际物理 JobGraph，不能以包装 StreamGraph 冒充编译。
- JobVertex 是可以部署的算子链。所有节点并行度为 1、边为 FORWARD 且不启用 Checkpoint 时，一个 Source → Operator* → Sink 链合并为一个 JobVertex；否则每个节点独立部署。
- JobGraph 冻结 JobID、已解析的执行模式与 Configuration；构图校验不得创建 SourceReader、SinkWriter 或工作线程。保持原有线性图/容量限制。

## Execution Lifecycle

- ExecutionJobVertex 拥有 JobVertex 的并行 ExecutionVertex；ExecutionVertex 是稳定 Subtask 位置；Execution 是一个不可复用的 Attempt。首次 Attempt 为 0。设置 Core 的 execution.restart.max-attempts（默认 0）时，允许仅从真实完成的磁盘 Checkpoint **整 Job** 重新装配所有 Task/Coordinator/Writer，Attempt 单调递增；无 Checkpoint、损坏或 UID 不兼容立即失败。绝不在同一个 StreamTask 上重新启动，也不支持局部 Reader 热恢复。
- ExecutionGraph 唯一维护 JobStatus、提交线程、取消与结果 Future；EmbeddedJobClient 只是查询/控制句柄，不能再维护第二套状态或 Worker。
- TaskDeployment 依据物理 JobVertex/JobEdge 创建 Task、ResultPartition、InputGate、OperatorChain、SourceCoordinator，并将每个 StreamTask 绑定到所属 Execution。它只负责装配、启动顺序、等待与资源清理，不成为另一层 Runner。
- 已删除 JobRunner、StreamJobRunner、CompiledJobPlan、JobExecution；不能为了测试注入重新建等价平行入口。
- 失败先中止全部 InputGate、取消 Task，最终关闭 Task、Coordinator 和 Checkpoint Store。仅正常 END_OF_INPUT 时 finish Operator 和 flush(true) Sink。无界 Source 意外结束视为失败。

## StreamOperator / Sink / KeyGroups

- `OneInputStreamOperator` 是唯一单输入算子接口，`OneInputOperatorFactory` 创建它，复用 `StreamOperator` 的 open / finish / close 生命周期。独立 Operator 与 Sink 均由 `OneInputStreamTask` 的 Mailbox 管理，无独立的 `SinkOperatorStreamTask`。
- Sink 统一通过 `SinkWriterOperator` 创建 Writer、处理记录、flush 和关闭；单并行内联 `OperatorChain` 使用同一个 SinkWriterOperator，并以**Sink 节点**（不是 Source 节点）的 `RuntimeTaskInfo` 初始化 Writer。Sink 定时 Flush 使用 Core `WriterInitContext.getProcessingTimeService()`，由 Runtime TaskProcessingTimeService 仅做计时，实际回调必须在同一 Mailbox 处理，失败使 Task 失败。内联链需要保留 Source Task 的定时器能力和取消信号。
- Core 统一通过 `Sink.createWriter(WriterInitContext)` 获取实际 TaskInfo/Attempt/maxParallelism 和 Configuration 防御性副本；不保留 `SinkV2` 或旧无参 `Sink.createWriter()`。`SinkWriter.write(T, Context)` 是唯一写入方法。当前写入 Context 没有事件时间，timestamp 为 null、watermark 为 Long.MIN_VALUE。
- `CancellableSinkWriter.cancel()` 是非阻塞终止信号，可以在 Task Mailbox 外运行，用于中断正在阻塞的 JDBC/网络 I/O；不得调用 flush、commit 或 close。Task 线程仍唯一负责关闭资源，失败、取消和 close 不允许隐式最终 Flush。
- `StatefulSinkWriter` / `SupportsWriterState` 可由 AlignedCheckpointCoordinator 组合版本化 Writer State 快照与恢复；只有 StatefulSinkWriter 且无 SupportsWriterState 恢复合同的 Sink 仍须拒绝。不可宣称有 CommittingSinkWriter / Committer 或事务 Exactly-once。
- `PipelineOptions.MAX_PARALLELISM` 默认 128，可配置到 32768；物理 JobVertex、RuntimeTaskInfo 和 RecordWriterOutput 读取同一值，配置不能小于有效并行度。KEYED 使用 Flink 风格的 Murmur3 hash → KeyGroup → Subtask 范围分配；**尚不支持状态 Rescale**。
- 非 KEYED Checkpoint 指纹不变。对于 KEYED，已更新拓扑指纹以包含 KeyGroup 算法与 maxParallelism，故采用旧 hashCode % N 方案生成的 KEYED Checkpoint 不能直接恢复，需要明确迁移策略；不静默混用两套哈希。

## Source / IO / Checkpoint

- SourceCoordinator 的事件循环与各 StreamTask Mailbox 分离。Split 事件处理确认与 Split 数据消费/Checkpoint 确认不能混为一谈；Reader 与 Operator 只在所属 Mailbox 执行。
- AddSplitEvent 在 Coordinator 创建时使用 Source.getSplitSerializer() 版本化编码，每次交付只发送独立字节；SourceOperator 在 Task Mailbox 中用本地 Serializer 解码，不直接共享 Split Java 实例。坏数据/版本不兼容或投递失败会使整个 Job 失败。
- Core SourceEvent 只定义 Connector 语义；SourceReaderContext / SplitEnumeratorContext 通过 OperatorEventGateway 与 SubtaskGateway 双向传递 SourceEventWrapper。Coordinator 在调用 handleSourceEvent 前严格比对当前注册的 Job / Operator / Subtask / Attempt，拒绝迟到的旧 Attempt 事件；Enumerator → Reader 在所属 Mailbox 执行回调。
- SplitAssignmentTracker 保留检查点期间分配历史；全 Job 恢复时由完成快照中的 Enumerator State、Reader Split 状态和 Coordinator 分配快照共同重建分片。仅凭投递 ACK 或未完成的 Checkpoint 不允许重试。
- StreamTask 不再自建 Runnable 队列、固定控制 Mail 批量处理数量或 park 轮询。Mail 通过 TaskMailbox / MailboxExecutor 排队，只有 Task 线程可以执行；Future 只能在 Mail 的动作真正执行后确认。
- MailboxDefaultAction 每次处理一个 InputStatus 步骤；NOTHING_AVAILABLE 时等待 StreamTaskInput 的 getAvailableFuture，暂停默认输入但继续执行控制 Mail。Future 就绪或新的 Split/NoMoreSplits 控制事件可恢复输入；持续报告「已就绪但无数据」应明确报错，不能忙轮询。
- TaskMailbox 状态 OPEN → QUIESCED → CLOSED；退出前拒绝新 Mail，关闭时未处理的控制 Future 必须异常完成。Task 取消和异步失败必须唤醒等待状态，正常 END_OF_INPUT 才执行最终 finish。
- StreamTaskSourceInput / StreamTaskNetworkInput 统一实现 StreamTaskInput，提供非阻塞 emitNext 与 getAvailableFuture。ResultPartition 为每个上游 Task 创建其 ResultSubpartition；InputGate 为下游 Task 消费多个上游 Subpartition，轮询读取且共享单一缓存额度。生产者等待 Gate 可用容量的条件通知，不使用定时轮询。
- RecordWriterOutput 将分区选择交给独立 StreamPartitioner：FORWARD、REBALANCE 保留现有行为，KEYED 使用固定 maxParallelism 的 Flink 式 KeyGroup 分区，但不支持 Keyed State Rescale。全部生产者结束且 Gate 缓冲清空才会 EOF；失败/取消必须唤醒阻塞的发送者、等待输入的 Task 和 Checkpoint。
- AlignedCheckpointCoordinator 负责单 JVM Source → Operator* → Sink 对齐式 Barrier：Reader / Enumerator / 分配历史必须先序列化冻结，再解冻 Source；ResultSubpartition 保持数据/Barrier 顺序，Barrier 已入队的 Producer 在目标 InputGate 对齐前不能用后续数据占满共享缓存；InputGate 对齐后由 Task Mailbox 快照、传播 Barrier 与 ACK，全部 ACK 后原子持久化。正常 Producer EOF 缺失 Barrier 只拒绝该 Checkpoint，不应造成 Job 失败。
- 无 Operator/Writer 状态的快照保持 v1 二进制格式和原签名；有状态快照使用 v2 UID/subtask 命名状态，旧 v1 可读；CRC/Serializer/目录独占锁保留。OneInputStreamOperator 默认无状态；持久化状态须显式实现 CheckpointedStreamOperator。OperatorStateBackend 的键控状态只适用于 KEYED 输入归属范围，恢复时 Key Serializer 版本不匹配必须明确失败，不能当成未写过状态；不支持 Serializer Migration / Rescale。当前 at-least-once，不支持 Exactly-once。
- 当前 MailboxProcessor / InputGate / ResultPartition 都是单 JVM 实现，不等于 Flink 网络数据交换。可选整 Job Checkpoint 恢复是受限的本地重建，不是 Flink 的局部 Failover 或 Exactly-once；不引入远程 InputChannel、Credit-Based Flow Control、多输入/网络 Barrier、RPC、Slot、JobMaster 或 KeyGroup Rescale。

## Verification

修改物理图和生命周期必须验证：JobVertex / JobEdge、单并行 Chaining、多并行 Subtask/Attempt、失败/取消清理、SourceCoordinator SourceEvent 及版本化 Split 投递、坏版本拒绝、Checkpoint 恢复、无 Checkpoint 不重试。JDBC Source-only integration tests do not prove cross-database Sink delivery or CDC E2E. Real MySQL/PostgreSQL/Oracle acceptance is required before claiming those environments are supported end to end.
