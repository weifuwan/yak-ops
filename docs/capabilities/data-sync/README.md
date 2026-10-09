# Data Sync Capability

Status: Active

**当前分支状态（PR #1514 修复）**：离线/实时同步的前端、Controller、Service、DAO、Connector 和数据库结构已恢复。旧 `io.yak.ops.flow.runtime` 根包执行引擎已移除；尚未接入新的业务运行引擎，手动执行、调度触发及实时自动恢复会拒绝创建新执行实例。历史版本发布和验收材料仍按其原有版本记录理解。

Scope: Workspace 内的离线 / 实时同步定义、发布、调度与执行观察。Data Sync 管产品语义，YakFlow 管执行机制。

## Goal

```text
Task Definition → Published Task → Execution（DataSyncInstance）
                                      └─ Attempt → YakFlow → Source / Sink
```

本文件定义共同的任务与映射边界，并导航到专项契约；不按实现批次维护功能清单。

## Contract Map

| 关注点 | 权威正文 |
| --- | --- |
| 创建、编辑、上线、下线和 definitionVersion | [Task Publication Lifecycle](task-lifecycle.md) |
| Execution / Attempt 身份、状态、取消、指标与产品事件日志 | [Execution Retry / Attempt](execution-retry-attempt.md) |
| 运维中心聚合指标与时间范围 | [Operations Metrics Read Model](#operations-metrics-read-model) |
| 离线 Cron、启停、触发校验与恢复 | [Scheduler](scheduler.md) |
| 实时期望状态、进程重启与 CDC state | [Realtime Desired State](realtime-desired-state.md) |
| Schema / Logical Table 产品模型与版本边界 | [Schema / Logical Table](schema-logical-table.md) |
| Multi-Table Task / Table Route 持久化与兼容边界 | [Multi-Table Route](multi-table.md) |
| 类型、split、写入与 checkpoint 机制 | [YakFlow](../yak-flow/README.md) |

## Task Definition

Task 由 Workspace 拥有，名称在 Workspace 内唯一。数据源按 ID 引用，不复制凭证。v1.3 PR1 已建立稳定 Table Route 子资源：Task 拥有一个 Source Datasource 与一个 Target Datasource，Route 拥有 Source / Target Table path、Mapping、Auto Create 与顺序。PR4 的 OFFLINE 写 API 支持 1-50 条 Route，运行时冻结全部 Route；REALTIME 仍只支持单 Route；`syncType` 创建后不变。当前兼容 Route 的 Target 默认必须已经存在，只有显式 `autoCreateTable=true` 时才允许在运行前创建缺失目标表。

`runtime_config` 按类型解释：OFFLINE 使用 `DataSyncRuntimeConfig`，REALTIME 使用 `DataSyncRealtimeConfig`。Retry Policy 独立保存并在创建 Execution 时冻结。Task Editor 不要求普通用户填写这些引擎参数：新建普通任务省略 Retry Policy 时由后端物化 SMART Retry（最多 3 次、15 秒起始退避），历史 / 显式无 mode Policy 按 FIXED 兼容；编辑请求省略时保留该 Task 已持久化的具体配置。`writeMode` 是任务语义，不放进 runtime tuning。

任务不是不完整草稿：保存必须通过后端 Datasource / Catalog / 映射校验；发布和运行还会重新校验外部资源。字段、默认值与请求校验从 DTO / VO 读取，不在这里复制全部参数。

OFFLINE Runtime 从本版本开始支持 Task Policy 与 Execution Effective Config 分离。Task Editor 新建 OFFLINE 任务时由后端写入 `runtimeConfig.policy=AUTO`；历史未携带 policy 的 Runtime Config 按 `FIXED` 解释，显式 API Runtime Config 未声明 policy 时同样按 FIXED 处理，避免升级后改写历史调优值。

AUTO 只在根 Execution 创建时规划一次：先使用 Mapping 后的 Source Logical Schema 估算行宽；MySQL / PostgreSQL 以约 1MiB、Oracle 以约 512KiB 的目标 Batch 预算计算 read/write batch，并限制在 100～1000 行。只有 Mapping 后仍保留单整数主键时才通过 YakFlow JDBC Statistics Reader 读取 MIN / MAX / COUNT：10 万行及以下整表读取，更大表按 25 万 / 50 万 / 100 万目标行数分片，并按计划分片数选择 2 / 4 / 8 的保守 Source 并行度。统计读取失败不阻断同步，回退到整表单并行。最终 Effective Runtime Config 与规划摘要写入 Execution definitionSnapshot；同一 Execution 的所有 Retry Attempt 复用该快照，不重新计算。

## Datasource Scope and Mapping

Datasource 已绑定的 database / schema 是权威范围；Task 不能覆盖已绑定层级，只有未绑定 schema 可由任务选择。映射预览、保存、发布和执行不得依赖前端校验结果。

v1.3 PR1 起 Column Mapping 的稳定产品 owner 是 Table Route；Task 根记录上的 `mapping_config` 仅作为当前单 Route Runtime 的兼容投影：

```text
Table Route
└── mapping = null
    → 沿用系统默认的大小写不敏感同名映射

Table Route
└── mapping.columns[]
    → source + target 的显式一对一映射
    → 来源字段与目标字段分别大小写不敏感唯一
    → 数组顺序属于 Route Definition
```

PR1 保留旧单表 DTO 兼容，PR2 让 Execution definitionSnapshot 冻结 Route 集合，PR3 已开放 OFFLINE 表级 Runtime，PR4 新增显式 `tableRoutes[]` 写入与逐表 Schema Preview；旧单表 API 仍可不传 `tableRoutes`。显式 Mapping 支持字段改名、字段子集和字段重排，但不允许表达式、自定义 SQL、CAST 或 Transform，也不保存字段值。Mapping 仍属于可执行定义，真实变化继续推进 definitionVersion；Retry / Auto Recovery 继续复用原 Execution 已冻结 Mapping。

Schema Mapping Editor 已直接消费该任务级 Mapping Contract：已有目标表支持同名 / 同序 / 手动连线、删除与字段搜索；自动建表目标不存在时允许重命名目标字段。前端只维护字段身份与顺序，不判断 JDBC 类型兼容或主键合法性，所有编辑结果继续通过后端 Mapping Preview 验证。

Mapping-Aware Schema Resolution 把同一份有序 Mapping 投影为两套位置对齐的运行 Schema：

```text
Source LogicalTable
        +
mapping.columns[]
        ↓
Source Read Schema
保留来源字段名 / 按 mapping 顺序和子集
        ↓ TableRecord position
Target Logical Schema
同一位置改为目标字段名
        ↓
Target Compatibility / DDL Plan / Sink
```

因此字段改名与重排不需要引入 Transform；Source 只读取被映射字段，Sink 使用同位置的目标字段名写入。Schema Preview、保存 / 发布校验、Auto Create Table 与每个 Runtime Attempt 共用同一 Mapping 语义。

Target 多余 nullable 字段允许存在；当前 Catalog 尚未稳定暴露 Column Default，因此多余 NOT NULL 字段保守判为不兼容，不能把数据库可能存在的默认值当成已验证事实。

Catalog 字段先投影为 YakColumn，再复用 [JDBC 逻辑兼容规则](../yak-flow/README.md#jdbc-schema-compatibility)。Data Sync 不再定义另一套 `java.sql.Types` 分类或转换规则。

## Schema / Logical Table

v1.2 引入产品级 [Schema / Logical Table Contract](schema-logical-table.md)，用于把 Datasource Catalog 的物理数据库结构与 YakFlow Runtime Schema 分开。

当前分层：

```text
Datasource Catalog
        ↓
LogicalTable
        ↓
TableSchema
        ↓
Target Table Plan
```

LogicalTable 复用 Core Logical Type，不维护第二套类型枚举；同时拥有 Runtime 不需要的 comment / schemaVersion 等产品元数据。

v1.2 当前已经完成 Source Metadata Introspection、Logical Type Normalization、Target Table Planner、Auto Create Table Runtime 与 Schema Preview UI。Task Editor 直接消费后端 Preview Contract：目标表已存在时展示真实 Target Schema Compatibility；目标表缺失且显式开启 Auto Create 时展示计划 Native Type、warning / unsupported 与只读完整 DDL。自动创建新表会保留 Source Catalog 中的 Table / Column comment，Mapping 改名后的目标字段继续继承原字段注释；MySQL 内联 Comment，PostgreSQL / Oracle 使用后续 COMMENT ON 语句。Runtime 仍会在执行前重新读取真实 Catalog，只有目标表缺失、autoCreateTable=true 且计划 supported 时才执行受控建表 DDL。Logical Table persistence 与 Catalog refresh / diff 尚未实现。

## Offline Execution

当前跨库验收范围为 MySQL → MySQL / PostgreSQL / Oracle。离线运行由 OfflineSyncExecutionPlanner 构造 JDBC Source / Sink，交给本地执行引擎。

| Task writeMode | 执行映射 | 产品前置条件 |
| --- | --- | --- |
| APPEND | APPEND + INSERT | 保留原目标数据；重复运行可能重复写入 |
| OVERWRITE | OVERWRITE + INSERT | 需要 TRUNCATE 权限；清空已提交后失败不能恢复旧数据 |
| UPSERT | APPEND + UPSERT | Existing Target 必须有主键且 Mapping 覆盖全部目标主键；自动建表时 Mapping 必须包含全部 Source 主键并按目标字段名生成主键 |

写入事务、split 与重放限制见 YakFlow；离线无跨进程断点续跑保证。Cron 和 Retry 不改变所选写入语义。

### Offline Runtime Trace

离线 Attempt 将 YakFlow JDBC Runtime Trace 作为诊断旁路写入 `yak.ops.home/data/data-sync/execution-traces/{workspaceId}/{executionId}/attempt-{n}`。目录不进入 Data Sync 数据库，也不增加 Trace 明细表；容器部署如果希望保留历史诊断文件，必须像 Realtime CDC state 一样持久化 `yak.ops.home/data`。

每个 Attempt 使用有界异步队列接收 Trace Event，并按 32MB 生成滚动 JSONL 文件：

```text
attempt-1/
├── summary.json
├── trace-000001.jsonl
└── trace-000002.jsonl
```

Trace Store 是 best-effort：队列拥塞、磁盘或 JSONL 写入失败只能增加 droppedEventCount / Server WARN，不能反向把原本成功的数据同步改成失败。Summary 记录 Source Split、Sink Batch、SQL 模板、行数与耗时汇总；详细 JSONL 不记录 TableRecord 业务字段值或 Sink bind 参数，错误消息进入文件前统一经过 SensitiveUtils 脱敏。

读取接口只支持 OFFLINE Execution，并默认读取当前 Attempt；可显式指定已存在的 Attempt 序号：

```text
GET /api/v1/data-sync/instances/{id}/trace/summary
GET /api/v1/data-sync/instances/{id}/trace/source
GET /api/v1/data-sync/instances/{id}/trace/sink
```

Source / Sink 明细使用 Cursor 分页，pageSize 默认 50、最大 200，可按 SUCCESS / FAILED 过滤。Source API 只返回 Split FINISHED / FAILED 终态，Sink API 只返回 Batch COMMITTED / FAILED 终态；内部 JSONL 仍保留 PLANNED / STARTED / SINK_OPENED，用于 Summary 重建和未来诊断扩展。

Trace 的 Source rows 是 Connector 在 Split 终态观察到的读取量，Sink rows 是成功提交 Batch 的行数；它们是诊断事实，不替代 Execution / Attempt 的 readRows / writeRows 产品指标，也不能解释成 exactly-once 业务行数。当前不提供 Trace retention、对象存储、全文检索或 REALTIME Trace。

## Realtime Execution

Source 仅支持 MySQL CDC，Target 支持 MySQL / PostgreSQL / Oracle。Source 必须有主键，且 Mapping 必须包含全部 Source 主键字段；这些主键允许改名。Target 主键字段集合必须与“映射后的 Source 主键目标名”完全一致，顺序可不同，缺失、额外或错误映射均拒绝。自动建表开启时按主键 Mapping 生成目标主键，创建后仍重新 introspect 校验。

Task 层 `writeMode` 固定 APPEND，运行时使用 JDBC CHANGELOG，并非普通追加 INSERT。读写指标表示变更事件，不等于业务表行数；一次 UPDATE 可以产生 UPDATE_BEFORE 与 UPDATE_AFTER 两个事件。

状态目录、稳定 engine identity、凭证变更风险与续传前提统一见 [Realtime Desired State](realtime-desired-state.md)。不提供可信的最后 checkpoint 时间字段，不推测或伪造该时间。

## MySQL CDC Source Requirements

源端 Binlog、快照与复制权限等前置条件统一见 [MySQL CDC Connector](../yak-flow/README.md#mysql-cdc-connector)。本节保留手工 E2E 使用的入口；Source / Target 主键对应仍按上述产品校验。

## Product Responsibilities

数据集成负责 Task 定义、发布，以及围绕当前 Task 的只读运行详情：任务详情可以查看该 Task 的 Execution 历史、选中的 Execution 状态、指标、Attempt 历史和冻结快照。OFFLINE Execution 额外提供 Runtime Trace 驱动的执行诊断（Summary、Source Split、Sink Batch、错误上下文），既有产品生命周期事件保留为辅助审计；REALTIME 仍展示产品事件日志。Task Editor 仍只负责定义，不承载运行态；数据集成详情不提供 Run / Start / Stop。OFFLINE Task 列表提供一次手工 `运行` 快捷入口，仅允许已上线且当前没有 PENDING / RUNNING / RETRY_WAITING Execution 的任务调用既有 Run API，创建根触发类型为 MANUAL 的 Execution。REALTIME Task 列表提供 `启动 / 停止 / 重新启动` 生命周期快捷入口：启动与重新启动复用同一 Run API；停止取消当前活动 Execution，并由既有 Realtime Desired State 契约把用户运行意图置为 STOPPED。

运维中心当前前端负责跨 Task 聚合运行观察：OFFLINE / REALTIME 根页面都直接消费 Operations Metrics Read Model，不再以 Task / Instance 表格作为主视图。Task 详情和运维中心复用同一 Execution / Attempt 后端事实，不建立第二套运行模型。既有 Run / Start / Stop、Schedule Runtime 与 REALTIME Desired State 后端能力仍保留；当前 Dashboard 根页面不承载这些命令，Task 列表的运行快捷操作不改变 Task Detail 的只读边界。

历史 Execution 持有自身 syncType、任务版本与脱敏快照。查询历史不依赖当前 Task 发布状态；删除 Task 不删除已有运行历史，但当前 Task 详情需要 Task 本身仍存在。运维可执行任务查询限定已发布任务。

## Operations Metrics Read Model

运维中心不通过拉取 Instance 分页后在浏览器聚合指标。后端提供 Workspace-scoped 的 `POST /api/v1/data-sync/operations/dashboard`，请求明确传入 `syncType` 与 `range`；当前范围只允许 TODAY、LAST_7_DAYS、LAST_30_DAYS。

TODAY 使用小时桶，近 7 / 30 天使用自然日桶；后端补齐没有 Execution 的零值时间桶和全部 Execution 状态项，因此前端可以直接绘图，不再自行补洞。范围窗口基于应用 `LocalDateTime`，结束时间取请求时刻，不生成未来桶。

摘要与趋势基于 `yak_ops_data_sync_instance` 的 Execution 根记录：execution / success / failed / lost / auto recovery、readRows / writeRows 与完成 Execution 平均耗时均按所选范围聚合。currentActiveTaskCount 是当前 PENDING / RUNNING / RETRY_WAITING Execution 的去重 Task 数，不受历史时间范围限制。abnormalTaskCount 是所选范围内出现 FAILED / LOST Execution 的去重 Task 数。

readRows / writeRows 继续遵循 [Execution Metrics Semantics](execution-retry-attempt.md#metrics-semantics)：它们是每个 Execution 当前或最终 Attempt 的镜像后再跨 Execution 求和，不累计同一 Execution 的多个 Attempt，也不代表去重业务行数或事务提交证明。Failure Ranking 按 FAILED + LOST 次数取 Top 5，并保留两个状态的独立计数。

当前没有 Runtime Metrics Time Series，所以 REALTIME Dashboard 不能把本读模型解释为 events/s、分钟级吞吐、CDC Lag 或 Checkpoint Lag。要展示这些连续指标必须先建设独立的 Metrics Snapshot，而不是从累计 readRows / writeRows 反推。

## Current Capability Boundary

当前 Runtime 仍是单节点。v1.3 PR1 新增稳定 Table Route 持久化、PR2 冻结有序 `tableRoutes[]` 与创建 PLANNED Table Execution、PR3 开放 OFFLINE N Route 顺序执行及独立 Attempt / Retry / Metrics。旧单 Route 仍走兼容 Executor，REALTIME 多表尚不支持。v1.2 已具备 Schema / Logical Table Contract、Source Metadata Introspection、Logical Type Normalization、跨 MySQL / PostgreSQL / Oracle 的 Target Table Planning、Schema Compatibility、显式 Auto Create Table Runtime、Schema Preview UI、任务级 Column Mapping、OFFLINE AUTO Runtime Planning、SMART Retry 与 Durable RETRY_WAITING Recovery。普通 Task Editor 不暴露底层 Runtime / Retry tuning；系统负责物化默认 Policy，Execution Detail 展示冻结后的 Effective Config / Retry Policy 事实。

发布、Schedule、启动自动恢复以及列表级 OFFLINE 运行、REALTIME 启动 / 停止 / 重新启动都是已有能力，不再列为“后续阶段”。Multi-Table Route 的当前边界见 [Multi-Table Route Contract](multi-table.md)：PR1 完成 Route 持久化，PR2 完成 Definition Snapshot + PLANNED Table Execution，PR3 开放 OFFLINE 多表 Runtime、单表重试和指标；Multi-Table Editor 尚未开放。Catalog refresh / diff、Automatic Schema Evolution、Incremental、Continuous Realtime Reconciliation 等能力继续按 v1.3 Release Contract 后续 PR 推进；Transform、分布式 Worker / HA / fencing 与 exactly-once 仍不在当前能力范围。

## Code and Verification

入口为 [DataSyncServiceImpl](../../../yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/impl/DataSyncServiceImpl.java)；实现职责见 [Data Sync Rules](../../../yak-ops-business/yak-ops-business-data-sync/DATA_SYNC_RULES.md)。

普通后端检查遵循 [Java Rules](../../../JAVA_RULES.md)，专项执行入口为 [Backend Acceptance](../../../.github/workflows/backend-acceptance.yml)。Column Mapping 的真实数据库验收由 `OfflineSyncJdbcAcceptanceIT` 与 `MySqlCdcIntegrationIT` 共同覆盖：JDBC 验证 MySQL Source 到 MySQL / PostgreSQL / Oracle 的字段子集、重排和改名；CDC 验证映射后主键在三类 Target 上的 Snapshot、INSERT、UPDATE、DELETE。Mapping Contract、Schema Resolver 或 Runtime Planning 相关路径变化会触发 JDBC / CDC Acceptance。状态机 / Quartz 验证不代替真实 JDBC / CDC 验收，连接器验收也不代替 [产品手工 E2E](../../e2e/data-sync/README.md)。一次执行结果留在相应 PR / CI 或版本证据。
