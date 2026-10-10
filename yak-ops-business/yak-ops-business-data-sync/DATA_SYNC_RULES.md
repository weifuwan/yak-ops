# Data Sync Rules

Status: Active

**当前分支状态（PR #1514 修复）**：离线/实时同步的前端、Controller、Service、DAO、Connector 和数据库结构已恢复。旧 `io.yak.ops.flow.runtime` 根包执行引擎已移除；尚未接入新的业务运行引擎，手动执行、调度触发及实时自动恢复会拒绝创建新执行实例。历史版本发布和验收材料仍按其原有版本记录理解。

Scope: `yak-ops-business/yak-ops-business-data-sync/**` 及 Common / DAO 中对应的 datasync 契约和持久化实现。

## Applicable Rules

遵循 [Architecture](../../ARCHITECTURE.md)、[Java Rules](../../JAVA_RULES.md)、[Business Rules](../BUSINESS_RULES.md)。DTO / VO、Entity、Repository 与 Migration 分别遵循 Common / DAO 的就近规则，不在本文件复制。

产品行为只由 [Data Sync Capability](../../docs/capabilities/data-sync/README.md) 及其发布、执行重试、调度、实时恢复专题定义。本文件管实现职责，不再维护另一份阶段、状态或字段清单。

## Business Boundary

唯一稳定产品 Service 为 `DataSyncService / impl.DataSyncServiceImpl`。它编排 Task、发布、Schedule 与执行请求；YakFlow 只负责数据平面执行。

Datasource 校验、Catalog 和运行连接必须经过 DataSourceService。禁止跨到 Datasource DAO / Plugin Registry，禁止复制连接模型或在 Business 维护 JDBC type-family 转换。

## Execution Package Organization

`io.yak.ops.business.datasync.execution` 根包不放具体实现，按已有职责聚合：

| 包 | 职责 |
| --- | --- |
| executor | OFFLINE / REALTIME Runtime 提交、单次尝试、指标 flush 和终态处理 |
| planning | 冻结快照 + Catalog / Connection 到执行计划；目标表 Preflight 归 `planning.target` |
| lifecycle | Execution / Attempt 状态迁移、进程内注册、取消和启动 LOST 处理 |
| realtime | CDC state identity、目录和 MySQL serverId 资源 |
| trace | Offline Attempt Runtime Trace 会话、文件持久化、Summary 和 Cursor 读取 |

executor 可以依赖 planning / lifecycle / realtime；lifecycle 和 realtime 不反向依赖 executor。OFFLINE / REALTIME 单表执行器的 Attempt 循环、durable Retry 等待统一归 `SingleTableAttemptRunner`；`ExecutionMetricsPoller` 统一采集单次 YakFlow 指标，失败状态仍交给 `DataSyncAttemptLifecycle` 持久化。多表 Route 调度与 Table Attempt Lifecycle 继续保持独立。`execution.executor` 不允许 nested 生产类型，独立语义的状态与结果使用顶层 Java 文件。不要复制 offline/planning 与 realtime/planning 层级、逐类建包或重建 Manager / Coordinator。`planning.target` 只收口 Target Runtime Preflight 及其 DDL 副作用边界，不再继续按单类拆子包。

测试按对应职责组织；直接代码入口见 [execution 目录](src/main/java/io/yak/ops/business/datasync/execution)。

## Schema Ownership and Existing Target Contract

The Data Sync business layer owns Workspace-scoped Task/Route definitions, publish/unpublish, scheduling and operations. It does not own a second physical Schema, JDBC Catalog, Dialect, Converter, SQL DDL Planner or executable Mapping engine.

- `DataSourceService.queryTableSchema(dataSourceId, tablePath)` performs Workspace-scoped discovery through `JdbcCatalogFactory` and returns the Connector-normalized Core `TableSchema`. Do not reintroduce `DataSourceColumn`, `JdbcSchemaMapper`, `LogicalTableNormalizer` or `SourceTableIntrospector`.
- `DataSyncServiceImpl` validates that source and target physical tables already exist. Source columns must have compatible target columns of the same name; target-only NOT NULL columns are rejected when their defaults cannot be verified.
- Compatibility is `io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility` over Core `Column` types. Business must not inspect `java.sql.Types` or rebuild a per-vendor type conversion switch.
- REALTIME and OFFLINE UPSERT require complete matching source and target PK sets. The Connector owns physical SQL column order and RowData positioning.
- Column rename/subset/reorder editing, Mapping Preview, automatic CREATE TABLE and DDL Preview are retired. Older persisted Task/Route flags must be cleared by explicit edits or rejected when used for new execution; never reinterpret them silently.
- No new Product-level `LogicalTable / LogicalColumn` model until a distinct persisted modeling capability exists. Avoid framework abstractions with one caller.

## Task and Route Implementation

- `DataSyncTaskDTO.tableRoutes[]` provides explicit OFFLINE ordered routes (1–50); REALTIME remains single-route. Task retains its first-route compatibility fields until a separate persistence migration. Do not add another representation of table identity.
- Task/Route edit must preserve Route IDs, validate Workspace ownership and reject repeated source or target paths. `DataSyncTableRouteDefinitionService` owns stable Route order/reconciliation only.
- Route updates, Task version bump and Task write occur in one product DB transaction. Existing source/target Catalog validation must not create DDL.
- New Task runtime/retry policy defaults are materialized by Product Service. Explicit updates must preserve previously stored tuning when omitted; do not recreate obsolete AUTO executor/planner components.
- Published v1.2 Flyway migrations and historical Execution snapshots are immutable. V4 multi-table Route storage remains a separate evolution boundary.
- Task create/update/publish, Quartz schedule, historical Instance/Attempt/Events and Operations queries remain available; OFFLINE/REALTIME runtime submission is deliberately not yet connected to new YakFlow.
- Keep `DataSyncServiceImpl` as the stable product Service entry; do not split it into Manager/Coordinator layers as part of this Catalog cleanup.

## Execution and Metrics Implementation

- v1.3 PR3 的 OFFLINE 多表只由 MultiTableOfflineExecutor 按冻结 Route 顺序执行；每个 Root 同时只运行一张表，不能把多表逻辑塞进旧单表 Attempt Loop。Table Attempt 与 Table Execution 状态迁移集中在 DataSyncTableAttemptLifecycle，不能由 Executor 随意覆盖终态。
- 多表 Root Status 是表级终态聚合：单表 FAILED 不取消后续表；所有表结束后只要有 FAILED / LOST，Root 就不能 SUCCEEDED。Retry 不重放该 Root 中已 SUCCEEDED 的 Route。
- 表级 Metrics 是本表当前或最终 Attempt 镜像，Root 汇总每表的最后镜像；禁止累计失败历史 Attempt 后重复计数。单个 Root 的工作线程可在内存维护各 Table Execution 最新指标以减少周期性数据库聚合，但最终终态必须由持久化表级事实计算；Retry 开始时必须重置该表镜像，绝不叠加历史 Attempt。SMART 对 APPEND / OVERWRITE 的启动后重放限制同样适用于 Table Attempt。
- Cancel 必须停止后续 Route，正在运行的 LocalExecution 收到 cancel；启动 LOST 时同步标记未结束 Table Execution 和 Attempt，不能误把它们标成 SUCCEEDED。取消和 Runtime 启动使用同一进程内令牌互斥；Runtime 工作线程实际退出后才能清理活动引用或释放 CDC serverId。
- 表级 Attempt Trace 使用 Table Execution ID 命名空间避免多张表相同 attemptNo 发生文件覆盖，安全与脱敏规则不变。
- v1.3 PR2 起 Root definitionSnapshot 必须冻结有序 `tableRoutes[]`；每条 Route 保存稳定 routeId、Source / Target endpoint、Mapping、Auto Create，以及 OFFLINE Route 自己的 Effective Runtime Config / planning summary。Root 上旧单表字段只允许作为首 Route 兼容投影。
- Root Execution 创建后、Runtime 提交前必须为每条冻结 Route 创建一条 Table Execution；PR2 状态固定为 PLANNED，禁止在没有 PR3 表级 Runtime 的情况下假写 RUNNING / SUCCEEDED。
- Table Execution identity 使用持久化 ID + routeId，禁止用 table name 拼接；同一 Root 内 routeId 与 routeOrder 都必须唯一。
- PR2 仍禁止 N>1 Route 进入当前单表 Executor；这个阻断只能由 PR3 在真正逐表执行闭环后移除。
新 Execution 先保存脱敏快照，再提交 Runtime；有事务时在提交后派发，不让执行依赖尚未提交的产品记录。

OFFLINE / REALTIME 共用 DataSyncAttemptLifecycle。状态变更使用 Repository 的预期状态条件更新，竞争失败不能当作已成功转移；取消和重试不得复活终态根记录。单表 Attempt 必须先完成 RUNNING 状态 CAS，再启动 YakFlow；进程内 Registry 应在启动窗口就预留取消令牌，不能等 Runtime 已启动才注册。Root / Attempt 指标只允许在 RUNNING 时写入，取消后不得覆盖终态指标。

活动集合、backoff、root trigger 及指标唯一语义见 [Execution Contract](../../docs/capabilities/data-sync/execution-retry-attempt.md)。实现必须按 Runtime / Attempt 区分计数与 Execution 当前尝试镜像；不得对根记录使用跨 Attempt 的“只增不减”修补或累计总量。

本地注册表仅持有活动 Runtime 的取消引用；启动将旧进程活动记录标记 LOST，不能把数据库 RUNNING 当作仍有本地执行对象。连续 Source 意外完成不标记 SUCCEEDED。

## Operations Metrics Read Model

运维聚合查询通过专用 DataSyncOperationsMetricsRepository / Mapper 读取 Execution 根记录，不复用分页接口后在 Business 或前端全量 groupBy。SQL 必须带 workspaceId、syncType 和受控时间范围；当前最大历史窗口为 30 天。

Business 负责解析 TODAY / LAST_7_DAYS / LAST_30_DAYS、补齐连续时间桶与状态零值，并把 DAO 聚合结果映射为 HTTP VO。DAO 不拥有产品时间范围枚举，前端也不重复计算时间桶。

聚合 readRows / writeRows 只能使用 Execution 当前 / 最终 Attempt 镜像；禁止 join Attempt 后求和，否则 Retry 会重复计算。currentActiveTaskCount 明确是当前快照，其他 summary / trend / status / failure ranking 明确属于所选时间窗口。

在没有独立 Metrics Snapshot 前，不新增 TPS、events/s、CDC Lag、Checkpoint Lag 等伪时间序列字段，也不从两次累计值查询差分冒充稳定 Runtime 指标。

## Execution Event Implementation

Execution 产品日志复用 lifecycle owner 持久化有限状态事件，不建立第二套 Server Log 系统。事件必须 Workspace-scoped，可选关联 Attempt，并使用 Common 中的事件级别 / 类型枚举；查询仍通过稳定 DataSyncService 进入 DAO Repository。

只记录低频生命周期事实，禁止把 metrics flush、每批 Source / Sink、SQL Debug 或任意 Logback 行写入事件表。用户可见 message 必须在持久化前经过 SensitiveUtils 脱敏并限制长度；事件表不得成为连接凭证或异常原文的旁路泄漏点。

产品事件用于观察，不控制 Runtime 结果：事件持久化失败记录普通 Server Log，但不得反向把本可成功的 Execution / Attempt 改成失败。历史记录不做推断回填。

## Runtime Trace Implementation

Runtime Trace 与 Execution Event Table 是两套不同职责：产品 Event Table 只保存低频生命周期事实；Source Split / Sink Batch 诊断明细进入 trace 文件边界，禁止为了查询方便把高频 Batch Event 再写回 MySQL。

Offline Executor 按 Attempt 打开 / 关闭 ExecutionTraceSession，同一个 Listener 同时交给 JDBC Source / Sink。Trace 会话使用有界异步队列，Listener 调用不能执行文件 I/O；队列满、Writer 失败或 Session 收口失败都不能改变 Runtime 终态。错误文本进入 Trace Record 前复用 SensitiveUtils，禁止记录 YakRow 字段值、Sink 参数或连接凭证。

File Store 根目录从 yak.ops.home 下的 data/data-sync/execution-traces 解析，Workspace / Execution / Attempt 路径必须做 segment 校验，不能接受任意相对路径。JSONL 使用滚动分片，Summary 通过临时文件 + replace 收口；进程异常导致缺少 Summary 时允许从已落盘 JSONL best-effort 重建，不伪造 droppedEventCount。

Trace 查询仍经 DataSyncService 校验 Workspace 与 OFFLINE Execution；HTTP 不直接暴露文件路径。明细使用受控 pageSize + Cursor 读取，禁止一次性把整个 Attempt Trace 加载进内存。Trace rows / durations 是诊断口径，不替代 Execution Metrics，也不从 Trace 反算吞吐时间序列。

## Scheduler and Recovery Implementation

- `scheduler` 下只定义框架无关 Contract；org.quartz.*、JobFactory 和最终启动装配归 Boot。
- onFire 重读数据库，不能直接信任 Quartz JobData 的授权或状态；Schedule / Runtime 边界见 [Scheduler](../../docs/capabilities/data-sync/scheduler.md)。
- Runtime 更新安排在提交后，但不能把它描述为与 DB 原子提交。不得用 Quartz Refire 实现产品 Retry。
- 启动恢复遍历跨 Workspace 任务时，显式绑定所属 Workspace，并在 finally 清理，不能泄漏上下文到下一任务。
- 启动恢复必须区分 Runtime ownership 与 durable wait：PENDING / RUNNING 标 LOST；RETRY_WAITING 保留原 Execution，并用持久化 currentAttempt / nextRetryTime / definitionSnapshot 恢复下一 Attempt。
- Durable Retry 恢复必须先于 REALTIME desired-state AUTO_RECOVERY；保留的 RETRY_WAITING 是 Active Execution，禁止为同一 Task 再创建第二个根 Execution。
- RETRY_WAITING 恢复失败时只允许把该根 Execution 收口 LOST；不得绕过原 root trigger / taskVersion 新建“补偿 Retry”根记录。
- REALTIME 目录和 serverId 生命周期留在 realtime；offset / schema-history 内容留在连接器。恢复条件与限制见 [Realtime Contract](../../docs/capabilities/data-sync/realtime-desired-state.md)。

## Secret and Persistence Boundary

运行连接只在可信执行规划阶段解析。Task / Execution / Attempt / Execution Event / Retry Policy、HTTP 响应、日志和异常不得泄漏原始凭证。ExecutionPlan 为内存对象，不能持久化或序列化到响应；快照不得包含连接 JSON、密码、SSH 私钥、Token、offset 结构或运行时租约。

Data Sync Entity / Mapper / Repository 和全部 Schema 归 DAO；不重复定义 DAO 模型、不建立数据库物理外键。历史 Execution 自存 syncType 等身份，不通过 join 当前 Task 推断历史；Task 删除不级联删除历史记录。

迁移遵循 [Flyway Rules](../../yak-ops-dao/FLYWAY_RULES.md)。V1 是冻结基线，Schedule / Attempt / Desired State / Execution Event 均由连续向前迁移定义，不能按旧阶段计划重复建表或回改已冻结 SQL。

## Verification

普通编译 / 格式 / verify 使用 [Java Rules](../../JAVA_RULES.md)。专项执行与路径过滤由 [Backend Acceptance](../../.github/workflows/backend-acceptance.yml) 定义，不在这里复制命令。

现有验证职责：业务状态测试检查身份、状态与命令；Quartz 测试检查 Cron / Time Zone / Misfire / next-fire；JDBC / CDC 真实数据库验收检查数据与恢复；[手工 E2E](../../docs/e2e/data-sync/README.md) 检查产品完整链路。

不得以跳过测试、仅 H2、业务替身或单次连接成功替代对应验收证据。验证结果只对实际提交与场景有效，记录在 PR / CI 或版本 Evidence，不追加阶段完成清单。
