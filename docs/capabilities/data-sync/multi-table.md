# Data Sync Multi-Table Route Contract

Status: Active — v1.3 PR4 Multi-Table Editor + Schema Preview

Scope:

- Data Sync Task 与稳定 Table Route 的产品边界
- v1.2 单表 Task → one Route 的兼容迁移
- Route persistence / identity / ordering
- PR1 兼容读写边界

Depends On:

- [Data Sync Capability](./README.md)
- [v1.3.0 Release Contract](../../release/v1.3.0.md)
- [Task Publication Lifecycle](./task-lifecycle.md)
- [Schema / Logical Table](./schema-logical-table.md)

## 1. Goal

v1.3 不再把 Source / Target Table identity 长期绑定在 Task 根记录上。

稳定模型是：

```text
DataSyncTask
├── sourceDataSourceId
├── targetDataSourceId
├── writeMode / runtime / retry / schedule / lifecycle
└── TableRoute[]
    ├── Route A
    ├── Route B
    └── Route C
```

Table Route 表达一条稳定的：

```text
Source Table
    ↓
Target Table
```

后续 Incremental Cursor、Schema Baseline、Table Execution、Route Metrics 与 Health 都以 Route ID 作为产品身份，不通过表名字符串拼接身份。

PR1 已建立 Route Contract + Persistence；PR2 进一步让 Root Execution 冻结全部 Route，并为每条 Route 创建稳定 Table Execution。PR3 已开放 OFFLINE 逐表 Runtime、失败隔离和表级 Retry / Metrics；PR4 进一步开放多选来源表、目标表路由配置与逐表 Schema Preview。

## 2. Ownership

### Task owns shared policy

Task 继续拥有：

- Workspace / Name / syncType。
- Source Datasource ID。
- Target Datasource ID。
- Write Mode。
- Runtime Policy / Retry Policy。
- Schedule。
- Published / Desired State。
- definitionVersion。
- Remark。

一个 Task 固定：

```text
1 Source Datasource
1 Target Datasource
N Table Routes
```

### Route owns table semantics

每条 Route 拥有：

- stable route ID。
- source database / schema / table。
- target database / schema / table。
- Column Mapping。
- Auto Create Table。
- Task 内 sortOrder。

Route 不保存 Datasource credential，也不复制 Task 的 runtime / retry / schedule / lifecycle。

Mapping 与 Auto Create 从 v1.3 架构上属于 Route；PR1 为兼容当前单表 Runtime，Task 根记录上的历史字段暂时保留为单 Route projection。

## 3. Persistence Contract

PR1 新增：

```text
yak_ops_data_sync_table_route
```

关键约束：

```text
id                    stable route identity
workspace_id          workspace isolation
task_id               owning task
source_*              source table path
target_*              target table path
auto_create_table     per-route policy
mapping_config        per-route mapping
sort_order            stable order
```

规则：

- Route 使用 Yak Ops 统一 String snowflake ID。
- 不建立数据库物理外键。
- `(workspace_id, task_id, sort_order)` 唯一，防止一个 Task 出现不确定顺序。
- Repository 查询必须显式带 Workspace。
- Route 不是 opaque JSON；后续状态可以稳定引用 route ID。
- Task 删除由 Data Sync Service 在同一业务事务中删除当前 Route；历史 Execution 仍不级联删除。

## 4. v1.2 Compatibility Migration

v1.2 已发布 Task 仍把表级定义保存在：

```text
source_database / source_schema / source_table
target_database / target_schema / target_table
auto_create_table
mapping_config
```

v1.3 Draft Migration 将每个现有 Task 回填为一条 Route：

```text
Existing Task
        ↓
Route #0
```

回填时：

- Route ID 复用已有 Task ID，得到确定且稳定的 identity。
- taskId 仍指向原 Task。
- sortOrder = 0。
- Source / Target path、Mapping、Auto Create 原样复制。
- create / update audit 保留原 Task 的值。
- 不更新 Task `definitionVersion`。
- 不修改历史 Execution / Attempt / Event。
- 不修改历史 `definitionSnapshot`。
- 不启动任何 Runtime。
- 不修改 REALTIME desiredState 或 CDC state identity。

复用 Task ID 只用于 v1.2 历史 one-route 回填；v1.3 新建 Route 使用正常的独立雪花 ID。

## 5. PR1 Compatibility Write Boundary

PR1 还没有开放多表写 API。

当前创建 / 编辑请求仍是 v1.2 单表 DTO。为了保持 Runtime 与前端兼容，Data Sync Service 在同一事务中维护：

```text
legacy Task single-table projection
        +
one persisted Table Route
```

创建：

```text
insert Task
   ↓
insert Route #0
```

编辑：

```text
update Task projection
   ↓
update same Route identity
```

删除：

```text
delete Task
   ↓
delete current Routes
```

事务中任一持久化失败都不能留下 Task / Route 半完成状态。

如果后续已经存在多条 Route，旧单表编辑入口必须拒绝修改，不能用一条旧 DTO 静默覆盖多表定义。

## 6. Read Boundary

Task 详情和创建 / 编辑响应可以返回：

```text
tableRoutes[]
```

PR1 正常情况下只有一条 Route。

Task 分页列表暂不展开 Route 明细，避免为每行 Task 产生额外 Route 查询和大 payload；列表仍沿用当前摘要字段，PR4 再设计多表摘要 UI。

## 7. Definition Snapshot

PR2 起，新的 Root Execution snapshot 以 `tableRoutes[]` 冻结完整表级定义：

```text
definitionSnapshot
├── task shared policy
└── tableRoutes[]
    ├── routeId / sortOrder
    ├── source endpoint
    ├── target endpoint
    ├── mapping
    ├── autoCreateTable
    ├── effective runtime config
    └── offline runtime plan
```

OFFLINE AUTO Runtime Planning 按 Route 独立计算并冻结，因此不同表可以拥有不同 Effective Config。Retry / Recovery 必须复用 Root Execution 已冻结的 Route Snapshot，不能重新读取 Task 当前 Route 覆盖历史执行。

为了保持 v1.2 单表 Executor 兼容，Root Snapshot 暂时继续保存首 Route 的 `source / target / mapping / autoCreateTable / runtimeConfig / offlineRuntimePlan` 投影。该投影只用于过渡兼容，新的 canonical 表级定义是 `tableRoutes[]`。

历史 v1.2 Snapshot 没有 `tableRoutes[]` 时继续按旧字段读取，不回填或改写历史 JSON。

## 8. Table Execution

PR2 新增：

```text
Root Execution
├── Table Execution A
├── Table Execution B
└── Table Execution C
```

每个 Table Execution 固定：

- Root executionId。
- frozen routeId。
- routeOrder。
- 独立表级状态 / Attempt 序号 / 指标 / 时间 /错误字段的持久化位置。

PR2 创建 Table Execution 时状态固定为：

```text
PLANNED
```

它只表示“该 Route 已被冻结并拥有稳定表级执行身份”，**不表示表级 Runtime 已经启动**。PR3 才允许 Table Execution 进入 PENDING / RUNNING / RETRY_WAITING / terminal 状态，并把 Attempt / Metrics 真正归到表级执行。

Root Execution 与全部 Table Execution 在 Runtime 提交前创建；任何 Route identity 都不能通过 Source / Target table name 临时拼接。

## 9. OFFLINE Multi-Table Runtime (PR3)

OFFLINE Task 通过 persisted Route 集合逐条服务端校验，Root 创建时冻结 `tableRoutes[]`，运行后按冻结顺序执行每张表：

```text
Root Execution (RUNNING)
├── Table Execution users      SUCCEEDED
├── Table Execution orders     FAILED
└── Table Execution products   SUCCEEDED
        ↓
Root Execution (FAILED)
```

单张表最终失败不会阻止后续表继续运行；Root 在所有表结束后汇总为 SUCCEEDED 或 FAILED。当前单任务并发上限为 1（顺序执行），不使用无限线程、分布式 Worker 或复杂用户调优 UI。

表级生命周期由 `DataSyncTableAttemptLifecycle` 管理：

```text
PLANNED → PENDING → RUNNING
                      ├── SUCCEEDED
                      ├── FAILED
                      ├── RETRY_WAITING → RUNNING
                      ├── CANCELED
                      └── LOST
```

Table Execution 保留自己的 `routeId / currentAttempt / readRows / writeRows / error`。
`yak_ops_data_sync_table_attempt` 独立保存每张表内的 Attempt 历史，`(workspace_id, table_execution_id, attempt_no)` 唯一；成功的表在同一 Root 内永不被后续失败表的 Retry 重放。

表级 Retry 使用 Task Root 冻结的 Retry Policy，SMART 仍使用已有失败分类和写入安全限制。对于 OFFLINE APPEND / OVERWRITE，Runtime 启动后 SMART 不自动重放；显式 FIXED 策略仍保持其历史语义和重复写风险。不提供 exactly-once 承诺。

Root 的 `readRows / writeRows` 汇总各 Table Execution **当前或最终 Attempt 镜像**，不把一张表的历史重试行数相加，也不承诺是实际提交业务行数。

### Cancel / Restart

用户取消整个 Root 时停止后续 Route 的调度，正在运行的 LocalExecution 收到取消，未结束的 Table Execution 进入 CANCELED；成功表的历史记录保持 SUCCEEDED。取消不能重新启动已成功 Route。

单节点进程异常结束后，旧进程遗留的 PENDING / RUNNING Root 继续标为 LOST；相应未结束的表级执行与 Attempt 标为 LOST。PR3 **不承诺**在 APPEND 可能存在部分已提交数据时跨进程自动重放；增量恢复与健康治理留给后续 PR。

### Read Model

- `GET /api/v1/data-sync/instances/{id}`：对 OFFLINE 多表 Root 返回 `tableExecutions[]`，包含 Route identity、状态、行数和脱敏错误
- `GET /api/v1/data-sync/instances/{id}/tables/{tableExecutionId}/attempts`：按 Root + Table + Workspace 校验，查询该表 Attempt 历史

旧单表 Execution 继续使用原有 Root Attempt / Trace 查询模型。多表 Attempt Trace 内部按 Table Execution ID 隔离存储；对应独立 UI 和 Trace 页面由后续 PR 接入。

REALTIME CDC 仍严格保持单 Route；不把 OFFLINE 多表误写成“全库同步”或“实时多表同步”。

## 9.1 OFFLINE Multi-Table Editor / Save Contract (PR4)

普通 OFFLINE 编辑器保持“数据源 → 数据来源 → 数据去向 → 去向字段映射 → 调度配置”结构：

- Source / Target Datasource 始终属于 Task，只各选一次；数据库绑定层级不在编辑器二次配置。
- Source Catalog 可多选 1-50 张表，筛选/刷新由现有 Datasource API 提供。
- 每张 Source Table 生成一条稳定 Route，目标表名可修改，Auto Create 关闭时目标表用 Catalog Select，开启时为 Input。
- 每张 Route 独立字段映射、Schema Compatibility 与建表 DDL 预览；前端只调用后端，不能自己推算 JDBC 类型兼容。
- 全部 Route 的后端 Schema Preview 均 compatible 后才可保存，预览请求有限并行，不因一张表字段映射变化覆盖其它 Route。
- Route ID 从 Task 详情回显；编辑更新原 Route ID、新增 Route 不传 ID，删除 Route 不重写历史 Execution。
- Task、Route 变更在同一事务内提交；Source/Target 物理表身份防重，同一 Target Table 不允许被多个 Route 写入。
- 旧单表请求省略 `tableRoutes` 时保留原有行为；REALTIME 不接受 `tableRoutes` 显式多表定义。
- Route 增删、排序和表级字段变化会推进 definitionVersion，只有名称/备注变化则不推进；Root Snapshot 继续冻结执行时所有 Route。
- `writeMode / Runtime Policy / Retry / Schedule / publication` 仍归 Task，不做 Route 级复杂策略 UI。

PR4 不做前端全库勾选/通配规则、不允许自动持续发现新表、不开放 REALTIME Multi-Table。

## 10. definitionVersion

Migration 只把现有定义投影为 Route，不能推进 `definitionVersion`。

PR1 期间通过旧单表 API 修改 Source / Target / Mapping / Auto Create 时：

- 继续使用已有 executable-definition comparison。
- 真正发生可执行定义变化时，Task `definitionVersion` 按原规则 +1。
- Route 同步本身不能再次额外 +1。

后续多 Route 编辑时，任何会改变冻结可执行 Route 集合或 Route 内容的变化都必须进入统一 definitionVersion 比较；该部分由后续 Contract 冻结。

## 11. Migration Status

v1.2 已发布历史：

```text
V1__baseline.sql
V2__v1_1_0.sql
V3__v1_2_0.sql
```

永久冻结。

v1.3 当前开发期 Draft：

```text
V4__data_sync_multi_table_route.sql
V5__data_sync_table_execution.sql
V6__data_sync_table_attempt.sql
```

这些文件只属于 v1.3 可重建开发 / E2E 历史；Release Freeze 前必须按照 Flyway Rules 一起审查并收口为最多一个正式 `V4__v1_3_0.sql`。

## 12. PR4 Non-Goals

- REALTIME Multi-Table CDC。
- 无界表级并行和 Distributed Worker。
- 增量 Cursor / 自动续传。
- Schema Diff / Evolution。
- Exactly-once 和跨库事务原子性。
- 失败的 APPEND 数据的安全自动补偿。

下一步：PR5 — Offline Incremental Sync Contract + Cursor State。
