# YakFlow Capability

Status: Active — Core / Runtime execution, Connector Base, JDBC Source/Sink; product JDBC/CDC integration pending

## Current State

旧 `yak-flow-connector-cdc-mysql` 与 Business 旧 execution 已删除。Core 提供统一 Source/Sink 协议和新的通用 TableRecord；旧 yak-flow-api 已移除；通用 RowData、TableRecord、RowKind、TableId 和 LogicalType / TableSchema 均由 Core 拥有。Runtime 具备单 JVM 执行基础，新 JDBC Source 已支持一个 Source 的多张表并行读取。**JDBC Sink 已支持单表/多表 APPEND 和主键 Changelog，真实跨库 Connector 验收独立存在；但业务任务执行入口与实时 CDC 尚未接入，不能宣称产品端到端同步已恢复。**

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
- 当前拓扑只支持单 Source、任意个单输入 Operator、单 Sink 的严格线性链；FORWARD / REBALANCE 行为不变。KEYED 由配置的 KeyGroup 上限驱动，不再按 hashCode 直接取目标并行度余数。
- StreamTask 的控制事件经过 TaskMailbox 串行处理，输入统一经 StreamTaskInput；MailboxDefaultAction 在 NOTHING_AVAILABLE 时挂起，不阻塞 Mail，Future 就绪或 Split 事件到来再读取。

## Local StreamTask Input and Physical Partitions

`StreamTaskSourceInput` 和 `StreamTaskNetworkInput` 实现相同的非阻塞 `StreamTaskInput`。跨 JobVertex 的每个上游 StreamTask 使用 `RecordWriterOutput` 与独立 `StreamPartitioner` 写入其 `ResultPartition`；每个下游 Task 通过 `InputGate` 消费所有生产者的 `ResultSubpartition`。输入 Gate 提供轮询公平性、统一队列容量、完成通知和可中断背压，而不是以前的混合 RecordChannel / RecordRouter。

FORWARD / REBALANCE / KEYED 路由保持兼容。KEYED 使用 Flink 风格的 Murmur3 KeyGroup 映射（默认 128 组），并为同 Subtask 的版本化 Keyed State 提供 KeyGroup 归属校验，但不提供状态 Rescale；本地 InputGate 支持单输入 Barrier 对齐，不实现远程网络 Credit。

## StreamOperator, Sink and KeyGroups

独立 OneInput 和 Sink 的 Task 统一由 OneInputStreamTask 启动，SinkWriterOperator 封装 SinkWriter 的 open、write、checkpoint flush、finish 和 close。单并行内联 OperatorChain 也使用相同 SinkWriterOperator。Core 的 Sink 统一要求 `createWriter(WriterInitContext)`，Writer 的记录上下文由 `SinkWriter.Context` 提供；有 SupportsWriterState 的 SinkWriter 可以随 Barrier 进行版本化状态持久化与恢复，没有恢复合同的 StatefulSinkWriter 继续拒绝；不提供事务 Committer。

pipeline.max-parallelism（默认 128）决定 KEYED 的 KeyGroup 数量，任务并行度只决定组如何归属各 Subtask，未来可据此设计 State Rescale，OperatorStateBackend 按固定 KeyGroup 提供命名/键控状态快照和恢复，但当前没有状态 Rescale。带 KEYED 的 Checkpoint 指纹会记录 KeyGroup 算法及最大并行度，拒绝使用旧 hashCode 路由模型的 KEYED 状态文件。

## Source Coordination

AddSplitEvent 使用 Connector 提供的 SimpleVersionedSerializer 生成版本化独立字节，Task Mailbox 接收后重新反序列化。Reader 与 Enumerator 使用 Core SourceEvent 和 Runtime SourceEventWrapper 双向传递自定义事件；Coordinator 校验 Job/Operator/Subtask/Attempt，旧 Attempt 的事件会被拒绝。全 Job 从已完成的 Checkpoint 恢复时合并 Reader Split 进度与 Coordinator 分片历史。当前不支持在原 Coordinator 内局部热替换 Reader。

## Checkpoint Boundary

Source → OneInputStreamOperator* → Sink 使用 AlignedCheckpointCoordinator 的单 JVM Barrier 对齐：冻结 Split 分配，在 Source Mailbox 快照 Reader 并发出有序 Barrier；各 InputGate 等全部生产者 Barrier 到齐才让 Task 在 Mailbox 内快照 Operator/SinkWriter、转发 Barrier、ACK；全部 ACK 后 FileCheckpointStore 原子持久化并通知 Source。Source 在 Barrier 发出后即可恢复生产，不再依靠全局 InputGate 排空。无 Operator 状态的快照继续写 v1，有状态快照写 v2；旧 v1 可读，CRC/UID/KeyGroup 指纹校验保留。启用 Checkpoint 时禁用内联 Chain。语义为受限 at-least-once，不是分布式 Flink Checkpoint 或 Exactly-once。

## JDBC Sink Changelog and Batch Lifecycle

JDBC Sink 暴露 `sink/JdbcSink`、`JdbcSinkBuilder`、`JdbcTableWritePlan` 和 `JdbcWriteMode`；`sink/writer/JdbcWriter` 继承 Connector Base 的 Mailbox 批处理合同。`internal/JdbcOutputFormat` 管理连接、事务和显式 Flush；`internal/executor` 包含仅一份待写记录缓冲和多表有序 PreparedStatement 执行。

当前 UPDATE_BEFORE / UPDATE_AFTER 是两个独立的 TableRecord：同一 Sink Subtask 必须连续接收同表的两半事件。主键不变时直接 UPSERT；主键改变时删除旧键再 UPSERT；完整的 DELETE 独立执行。BatchSize 与定时 Flush 不拆开半个 UPDATE，Checkpoint/正常结束在 Before 不完整时拒绝成功 Flush。未实现事件配对跨分区、XA、Exactly-once 或产品 CDC 任务自动装配；并行 CDC 需要上游保留一对更新的顺序与归属。

## Connector Reader Foundation

`yak-flow-connector-base` implements a reusable `SourceReaderBase` over Core's `SourceReader` contract. A dedicated `SplitFetcher` calls a connector-owned blocking `SplitReader`, transfers bounded record batches through a future-completing queue, and never advances mailbox-owned checkpoint state. The `RecordEmitter` runs on the mailbox after the record is delivered to `ReaderOutput`; completed split markers are applied only after all records in the fetch batch are consumed. Failure, cancellation and no-more-splits wake mailbox waiters. Each reader owns its fetcher and closes it with a configurable timeout.

The base module has no JDBC/CDC connection logic and does not change Runtime's SourceCoordinator, Barrier ordering or restart policy. A concrete Connector supplies its own split, reader I/O, emitter and serializer. The current reader interface has a flat output (no per-split watermark or event-time output), and this foundation does not claim end-to-end exactly-once.


## Core Logical Types and RowData

`yak-ops-core` owns `LogicalTypeRoot` and immutable `LogicalType` values, including type
nullability, character/binary lengths, decimal precision and scale, temporal precision, and stable
`asSerializableString()` descriptions. `Column` carries a name and one type; the type owns
column nullability and capacity. JDBC Catalog metadata with missing or out-of-engine-range
DECIMAL precision is explicitly `UnresolvedDecimalType`, and cannot be used by internal
`RowData.createFieldGetter` until a converter resolves it. Do not silently assign a default precision.

`RowData` is the storage-neutral field-access interface; `GenericRowData` is the sole
array-backed implementation for now. `TableRecord` continues to own `RowKind` and physical
table identity, so RowData does not duplicate those fields. Internal typed access follows the
resolved logical type: Java boxed primitive values, String, byte[], BigDecimal, and java.time
date/time values. The JDBC Source delegates to a vendor-specific JdbcDialectConverter, which converts
driver objects to the resolved GenericRowData representation before emitting records.
Unsupported driver types fail explicitly instead of silently entering RowData.

Binary/columnar RowData, Flink SQL Table API, custom aggregate types, and network serializer
implementations are outside this change. Typed getters are strict: they do not silently coerce
a vendor-specific JDBC object to the expected internal representation.

## JDBC Factory and Row Conversion

`JdbcFactoryLoader` uses Java `ServiceLoader` to discover the unique `JdbcFactory`
for a JDBC URL. Built-in providers include MySQL, PostgreSQL, Oracle, and H2/ANSI test
support; missing and ambiguous factory matches fail explicitly. A single factory creates
both a JdbcDialect and a Connector-owned JdbcCatalog, while Datasource's existing Catalog
is temporarily retained for product compatibility.

`JdbcConnectionProvider` is a serializable, injected capability to open an independent
caller-owned JDBC connection. `DriverManagerJdbcConnectionProvider` is the default; an
external product adapter can use the already isolated JDBC driver and SSH tunnel runtime
without making this module depend on Datasource, Business, or DAO. Enumerators and Reader
fetchers must not share a Connection.

`JdbcDialect.createRowConverter(ResultSetMetaData)` constructs a per-query
`JdbcDialectConverter`, which normalizes JDBC values into the Core RowData contract:
primitive wrappers, BigDecimal, String, byte[], and java.time values. Vendors can
override unusual SQL types (Oracle DATE as LocalDateTime, PostgreSQL JSON/JSONB/UUID
as String, MySQL unsigned integers with checked ranges). Unsupported nested types or
decimal precision above the engine's domain fail with an explicit SQL exception;
raw driver objects are not silently propagated. The same converter contract supports
binding canonical RowData to PreparedStatement for future JDBC Sink work.

ResultSet column count and ordering are verified against the assigned Split. This
does not yet freeze a complete logical TableSchema inside checkpoints or guarantee a
consistent snapshot of changing tables; those remain future Reader/Checkpoint work.


## Connector-owned JDBC Catalog

`database/catalog/JdbcCatalog` and `AbstractJdbcCatalog` implement a read-only
relational Catalog inspired by Flink JDBC Catalog: list databases/schemas/tables,
find exact table identities, resolve ordered logical columns and primary keys by
JDBC `KEY_SEQ`, and reuse the vendor DialectConverter for types. Every metadata
operation owns and closes its connection. A single `JdbcFactory` SPI supplies
both Dialect and Catalog, with `JdbcCatalogFactory` as the public creation entry point.

The package structure follows Flink JDBC Core's contracts: `database/dialect`
owns `JdbcDialect`, `AbstractDialect`, `JdbcDialectConverter`,
`AbstractDialectConverter`; `database/catalog` owns Catalog contracts and
the shared metadata implementation. Concrete MySQL, PostgreSQL, Oracle and H2
classes live under `database/internal/dialect`, `internal/catalog`, and
`internal/convert`. Concrete SPI factories live directly in `database/internal`.

This PR does not delete the Datasource JDBC Catalog or change current product
metadata callers. A later adapter PR can switch those callers to Connector Catalog;
do not create a second data-source Catalog implementation in the meantime.
PostgreSQL Catalog lists the currently connected database (it does not
automatically reconnect to other databases); Oracle uses owner/schema
namespaces and exposes the current schema as the database-equivalent selector.

## JDBC Source Reliability and Checkpoint

Every planned JDBC split freezes the selected column order, numeric key bounds and a
SHA-256 fingerprint of `TableId`, key identity, resolved logical types and nullability.
The Planner derives the fingerprint with a zero-row projected query using the same vendor
converter as the Reader. The Reader compares that fingerprint before emitting records;
changing a selected column's SQL type, scale, capacity or nullability fails the split
instead of attempting an unsafe checkpoint restore. Adding an unselected column does not
change the frozen query projection. Unplanned tables remain discoverable on recovery;
a Source checkpoint is **not** a transactionally consistent multi-table snapshot.

Split and Enumerator state serializers now use version 2. Version 1 checkpoints are
explicitly rejected rather than silently interpreted without a type fingerprint.
When migrating a prior engine checkpoint, start a fresh bounded snapshot/attempt.
Reader progress is still updated only after output, so prefetched rows are not
checkpointed as consumed. Enumerator assignment removes pending work only after
`assignSplit` succeeds, and in-flight asynchronous table planning is re-run on
restoration when the table index has not advanced.

`JdbcConnectionRetry` retries a bounded number of **connection creation** failures
classified as transient or SQLState class 08. The Reader also validates a reused
connection at each split boundary and reconnects if it is no longer valid. If the
connection drops while the next split's query is opening, it can retry before
emitting that split's first row. It never automatically restarts a query after
ResultSet consumption; that is a Runtime failure/restart boundary.
`SplitReader.cancel()` now distinguishes terminal cancellation from `wakeUp()`
used for new splits. JDBC cancellation signals the active Statement asynchronously so
it does not block the mailbox, while fetcher shutdown still has a bounded close timeout.
JDBC driver cancellation is best-effort and may fail when network I/O cannot be
interrupted; in that case the Runtime receives the close-timeout failure.

Behavior tests cover disconnected acquisition, mid-fetch SQL failure without hidden
retry, altered database schemas, no-key split replay, pending assignment retention,
concurrent cancellation and real MySQL/PostgreSQL/Oracle schema drift.

## JDBC Source

`yak-flow-connector-jdbc` implements one bounded `JdbcSource` over a frozen list of `TableId` values; a single table is the same path as many tables. A single `JdbcSourceEnumerator` discovers metadata and plans each table off the coordinator event loop; `JdbcSourceReader` uses Connector Base to read multiple independent splits per subtask, emitting `TableRecord` values with original table identity.

For tables with exactly one signed-long-compatible integer primary key, the planner uses disjoint inclusive range splits and resumes a split with an exclusive `lastEmittedKey` seek predicate. Progress is updated only by `JdbcRecordEmitter` after successful output, never by Fetcher prefetch. No supported key or out-of-range unsigned keys means one full-table split that is replayed from the beginning on recovery; this may produce duplicates. Source definition fingerprints reject changed table sets or configured column projections during enumerator restoration. Versioned split/enumerator serializers contain no credentials or active connections.

A caller can supply optional ordered columns per table in the JdbcSource constructor.
The Planner validates requested columns against Catalog metadata and freezes the
projection into each Split. When a numeric primary key is excluded from the
projection, JDBC fetches it as a hidden final column to advance the checkpoint
cursor, but emitted RowData contains only requested fields. The existing Split
serializer format stays at version 2.

The JDBC reader supports MySQL, PostgreSQL and Oracle quoted identifiers and read connection policies, plus ANSI/H2 for embedded integration tests. Target-table DDL, vendor dialects, JDBC conversions and the new read-only Catalog have one owner in JDBC Connector. Datasource's legacy Catalog and metadata mapping remain available only until a separate product adapter migration. JDBC Driver availability and read cursor behavior remain database/driver dependent. The JDBC Source is a bounded table scan, not a transactionally consistent cross-table snapshot or MySQL CDC. The tests exercise real embedded H2 ResultSets through local YakFlow Runtime; they are not MySQL/PostgreSQL/Oracle acceptance results.

## Non-Goals

本阶段不实现 JDBC Sink / MySQL CDC Connector、多个 Source/Sink、网络 Shuffle、Slot / RPC、局部 Reader-only Failover、动态扩缩容、跨进程状态恢复或完整分布式 Checkpoint。仅新增受 Checkpoint 约束的显式本地整 Job Attempt 恢复。不能用 Runtime 单元测试或旧版 Release Evidence 宣称这些能力。

## Related

- [Core / Runtime Execution Contract](core-runtime-contract.md)
- [YakFlow Rules](../../../yak-flow/YAK_FLOW_RULES.md)
- [Data Sync Product](../data-sync/README.md)
- [Datasource JDBC Catalog Mapper](../../../yak-ops-plugins/yak-ops-plugin-datasource/yak-ops-plugin-datasource-jdbc/src/main/java/io/yak/ops/plugin/database/jdbc/schema/)（Catalog-to-logical-schema conversion; SQL/DDL dialects live in JDBC Connector）
