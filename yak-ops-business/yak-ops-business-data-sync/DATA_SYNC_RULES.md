# Data Sync Rules

Status: Active

Scope: `yak-ops-business/yak-ops-business-data-sync/**` 及 Common / DAO 中对应的 datasync 契约和持久化实现。

## Applicable Rules

遵循 [Architecture](../../ARCHITECTURE.md)、[Java Rules](../../JAVA_RULES.md)、[Business Rules](../BUSINESS_RULES.md)。DTO / VO、Entity、Repository 与 Migration 分别遵循 Common / DAO 的就近规则，不在本文件复制。

产品行为只由 [Data Sync Capability](../../docs/capabilities/data-sync/README.md) 及其发布、执行重试、调度、实时恢复专题定义。本文件管实现职责，不再维护另一份阶段、状态或字段清单。

## Business Boundary

唯一稳定产品 Service 为 `DataSyncService / impl.DataSyncServiceImpl`。它编排 Task、发布、Schedule 与执行请求；YakFlow 只负责数据平面执行。

Datasource 校验、Catalog 和运行连接必须经过 DataSourceService。禁止跨到 Datasource DAO / Plugin Registry，禁止复制连接模型或在 Business 维护 JDBC type-family 转换。

## Business / Engine Boundary

- `impl.DataSyncServiceImpl` 继续负责 Task、Route、Schedule、Schema 预览与历史实例 / Metrics 读取；DAO 继续负责相关持久化。
- `execution.lifecycle` 当前只保留数据层的 Attempt / TableAttempt 状态和 Retry 分类；`execution.trace` 保留历史诊断数据契约和文件读取；`execution.planning.OfflineRuntimePlanner` 只做非执行期参数预估。
- 不恢复旧 `execution.executor`、旧 Runtime 提交器、Execution Registry / Recovery、JDBC / CDC Source / Reader / Sink。新实现必须通过 `yak-ops-core` 的公共契约接入。
- 没有新引擎时，`runTask`、`enableSchedule` 必须返回 `ENGINE_UNAVAILABLE`；不创建空转实例、不注册可执行 Cron、不在启动时自动恢复同步。
- Controller、前端、Task / Schedule、DAO、已有 Flyway Migration 不属于同步引擎清理范围，不能因拆除旧执行器而删除。

## Schema Package

`io.yak.ops.business.datasync.schema` 拥有 Data Sync 产品级 Logical Table 内存契约。根包只保留 `LogicalTable / LogicalColumn`；`catalog` 负责物理 Catalog → Logical Schema，`mapping` 负责 Mapping Projection，`target` 负责目标表 Planning / Compatibility。不得按 `resolver / manager / service / model` 技术后缀继续建包。

规则：

- LogicalTable / LogicalColumn 是产品 Schema，不是 Datasource Catalog DTO，也不是 YakFlow Runtime Entity。
- 逻辑类型必须复用 YakFlow `YakDataType`；禁止在 Data Sync 新建数据库类型大全或第二套 logical type enum。
- Source `typeName / jdbcType` 只用于 Catalog import / compatibility，不作为 Logical Schema canonical type。
- Logical Table 可以投影为 `YakTableSchema`，但 comment / schemaVersion / 产品资源身份不进入 Runtime。
- `SourceTableIntrospector` 只能通过 `DataSourceService.queryCatalogTable / queryCatalogColumns` 读取物理元数据，不能绕过 Service 访问 Plugin Registry 或 Runtime connection。
- `LogicalTableNormalizer` 负责 Catalog → LogicalTable 的纯归一；JDBC 类型统一复用 `JdbcSchemaMapper`。
- Catalog composite primary key 必须使用 `primaryKeyPosition / KEY_SEQ` 保留顺序，不能按字段 ordinal 猜测。
- Logical Table persistence 仍归 DAO；schema 包不直接持有 Entity / Mapper。
- `TargetTablePlanner` 只消费 LogicalTable + Target Type / Path，聚合产品级 warning / unsupported，不连接数据库、不执行 DDL。
- Target Native Type / identifier quote / CREATE TABLE + Comment DDL 归 YakFlow `JdbcDialect`；Data Sync 不复制 MySQL / PostgreSQL / Oracle 类型映射或注释语法。
- Target Plan 出现 blocking unsupported 时必须保持 `createTableSql=null` 且 `ddlStatements=[]`，不能生成部分 DDL 或静默降级。
- Target Table / Column comment 从 Source Catalog 进入 Logical Schema，Mapping 改名后继续跟随目标字段；自动创建新表时必须进入同一 Target DDL Plan。MySQL / PostgreSQL / Oracle 的具体 Comment 语法归 JdbcDialect。
- `TargetSchemaCompatibility` 是保存预览与 Runtime Preflight 共用的目标结构兼容口径；Source nullable → Target NOT NULL、缺失 Source 字段、类型/容量不兼容、以及 Target 多余 NOT NULL 字段都必须拒绝。
- `TargetTablePreflight` 每个 Attempt 重新读取 Catalog：目标存在只校验；目标缺失时只有 snapshot.autoCreateTable=true 且 Plan supported 才可调用 TargetTableDdlExecutor。
- CREATE TABLE + Comment DDL 后必须重新读取 Target Catalog 并再次做兼容性 / Primary Key 校验；禁止直接相信生成 DDL，也禁止自动 ALTER / DROP / COMMENT 已存在表。
- `autoCreateTable` 是 Task 可执行定义，默认 false；变化必须推进 definitionVersion，Execution Snapshot 冻结后 Retry / Auto Recovery 复用该值。
- 已冻结到 Task snapshot 的 Schema / auto-create policy 不得因后续 Task 编辑而改变历史 Execution。

新增 Schema 类型前必须通过 Abstraction Test：拥有独立规则 / 稳定结果，或被多个真实调用方以同一语义复用；否则优先放回现有 owner 或 private method。不要在 Business 内新增 `application` 包重复 Service Layer 语义。

具体产品语义见 [Schema / Logical Table Contract](../../docs/capabilities/data-sync/schema-logical-table.md)。

## Task and Mapping Implementation

- v1.3 PR4 OFFLINE 显式 `tableRoutes[]` 保存必须先逐 Route 解析 Datasource Scope、标准化 Mapping，并复用后端真实 Catalog / Schema Compatibility；不能信任前端的 compatible 状态。
- Route 增删/重排/更新与 Task version bump 在同一 `@Transactional` 业务事务内；旧 Route ID 必须保持，Request 中伪造/跨 Task / 重复 Route ID 必须拒绝，来源和目标表路径均不能重复。
- `DataSyncTableRouteDefinitionService` 只持有 Route 结构变更与持久化，不重新实现 Mapping / JDBC 校验；更新 Route 时须显式写入 NULL，支持清除旧 Mapping / Schema。
- 旧单表 DTO 的 `tableRoutes=null` 保持兼容。新保存显式传 1-50 条 Route；REALTIME 拒绝显式 Route 集合。Task 根单表字段仍只是首 Route 兼容投影。
- v1.3 起 Table Route 是 Source / Target table path、Mapping、Auto Create 与 route order 的稳定产品 owner；Task 根记录上的单表字段在过渡期只是 Runtime / API 兼容投影，禁止新增能力继续把表级状态绑定回 Task。
- Route 必须使用稳定 ID 持久化为独立子资源，不能存成 Task 内 opaque JSON；所有 Route Repository 查询都必须带 workspaceId。
- PR1 的旧单表写接口只允许维护唯一兼容 Route：创建 Task 后同事务创建 Route，编辑保持原 Route ID，删除 Task 同事务删除当前 Route。检测到多 Route 时旧单表编辑入口必须拒绝覆盖。
- v1.2 → v1.3 Migration 只把现有单表定义投影为 one Route，不推进 Task definitionVersion、不修改历史 definitionSnapshot、不触发 Runtime，也不重置 REALTIME CDC state。
- 通过 WorkspaceContext.requireWorkspaceId 获取产品请求范围；所有 Task / Schedule / Execution / Attempt 访问必须带 workspaceId，不能仅凭资源 ID 查询。
- Task 保存、发布、运行均按 [Task / Mapping Contract](../../docs/capabilities/data-sync/README.md#datasource-scope-and-mapping) 做服务端校验；前端值只在未绑定范围内参与选择。
- Task `mapping_config` 为可空 JSON：NULL 表示旧行为的隐式同名映射；显式 Mapping 必须 trim 字段名，并保证 source / target 分别大小写不敏感唯一。Mapping 只保存字段身份，不保存表达式、SQL、类型转换或业务字段值。
- SchemaMappingResolver 是显式 Mapping 的唯一 Schema 投影口径：Source Read Schema 保留来源字段名并按 Mapping 顺序 / 子集排列，Target Logical Schema 在相同位置使用目标字段名。禁止 Preview、Save Validation 与 Runtime 各自维护另一套 rename / reorder 算法。
- 字段改名、字段子集和重排可以直接进入 Preview / Runtime，但不引入表达式、CAST、自定义 SQL 或 Transform；YakRow 值通过 Source / Target Schema 的位置对齐传递。
- Mapping 是可执行定义：变化推进 definitionVersion，创建 Execution 时冻结进 definitionSnapshot；Retry / Auto Recovery 不读取 Task 最新 Mapping 覆盖历史根 Execution。
- TargetSchemaCompatibility 只消费已经投影成目标字段名的 LogicalTable；TargetTablePlanner / Auto Create DDL 同样消费映射后的 Target Logical Schema。继续复用 JdbcSchemaMapper / JdbcSchemaCompatibility，不在 Service 再写一套类型能力表。
- REALTIME Mapping 必须覆盖全部 Source PK，Target PK 按 Mapping 后的目标字段名比较；UPSERT Existing Target 要求 Mapping 覆盖全部目标 PK，UPSERT Auto Create 要求 Mapping 覆盖全部 Source PK。
- 版本比较集中在可执行定义的规范化比较，不每次 PUT 加一；包括 mapping 与 retryPolicy，具体语义见 [Version Contract](../../docs/capabilities/data-sync/task-lifecycle.md#definition-version-contract)。
- Runtime Config / Retry Policy 的默认与保留语义由 Data Sync Service 拥有：创建请求省略时物化当前系统默认值；编辑请求省略时保留 Task 已持久化的具体配置。省略字段不得被解释为“重置默认”，也不得因为 UI 隐藏参数而修改历史调优值。
- Retry Policy mode 只有 SMART / FIXED：新建且省略策略时物化 SMART(3 attempts, 15s base backoff)；历史 / 显式无 mode Policy 按 FIXED。SMART 分类归 `DataSyncRetryClassifier`，Attempt 状态迁移仍只归 `DataSyncAttemptLifecycle`，禁止 executor 自己写数据库状态。Executor 默认重试参数与 backoff 计算统一通过 `ExecutionRetryPolicy`，不在 OFFLINE、REALTIME、多表 Executor 中分别复制算法。
- SMART 仅重试明确瞬时异常。OFFLINE APPEND / OVERWRITE Runtime 启动后禁止自动重放；UPSERT / REALTIME CHANGELOG 可以对明确瞬时失败重试，但不得描述为 exactly-once。未知异常默认不重试。
- OFFLINE Runtime Policy 只有 AUTO / FIXED 两种稳定语义：新建且省略 runtimeConfig 时物化 AUTO；历史无 policy 或显式 Runtime Config 未声明 policy 时按 FIXED 兼容。AUTO 不是 UI 开关，不允许前端复制规划算法。
- `OfflineRuntimePlanner` 只在根 Execution 创建时把 AUTO Task Config 解析为 Effective Runtime Config；输入使用 Mapping 后的 Source Logical Schema、当前 Source Statistics 与 Target 类型。规划结果与摘要冻结进 definitionSnapshot，同一 Execution 的 Retry Attempt 禁止重新计算。
- AUTO Statistics 只复用 YakFlow `JdbcSourceStatisticsReader` 的受控 MIN / MAX / COUNT；统计失败属于优化降级，回退整表单并行，不得因为自动调优额外把原本可运行的同步判失败。
- CRUD / 查询、发布与运行的副作用必须分开；不能在保存或发布方法里偷偷启动 YakFlow。
- `DataSyncServiceImpl` 是稳定产品编排入口，不因为文件长度机械拆 Manager / Coordinator；已有 Schema / Execution owner 能承担的逻辑不得再以重复 private helper 复制。

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
