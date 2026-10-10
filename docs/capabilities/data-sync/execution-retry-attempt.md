# Data Sync Execution Retry / Attempt Contract

Status: Active

Scope: Execution / Attempt 身份、状态聚合、Retry、取消、指标、产品事件时间线和持久化兼容性。

## Identity

v1.3 PR2 在 Root Execution 与 Attempt 之间建立稳定 Table Execution identity：

```text
Task
└─ Root Execution
   ├─ Table Execution A (PLANNED)
   └─ Table Execution B (PLANNED)
```

PR2 只负责冻结 Route 与创建 Table Execution；既有 Attempt 仍是单表 Runtime 的 Root-level 兼容实现。PR3 才把 Attempt / Retry / Metrics 真正下沉为每个 Table Execution 的运行事实。不能把 PR2 的 PLANNED Table Execution 当成已执行状态。

```text
Task
 ├─ Execution E1（现有 DataSyncInstance）
 │    ├─ Attempt 1 FAILED
 │    └─ Attempt 2 SUCCEEDED
 └─ Execution E2
      └─ Attempt 1 RUNNING
```

一次手动运行、有效 Cron Fire 或启动自动恢复创建一个 Execution。Retry 只在该 Execution 内创建 attemptNo+1，不创建新的根记录，不复活旧 Attempt。最终失败后用户再次运行是新 Execution。

Execution 的 workspaceId、taskId、taskName 快照、taskVersion、syncType、root trigger、definitionSnapshot 和 Retry Policy 在创建时固定。所有 Attempt 使用同一冻结输入，不读取最新 Task 定义。

Attempt 拥有自身状态、指标、时间与脱敏错误。`(executionId, attemptNo)` 唯一，attemptNo 从 1 递增；不允许跨 Execution 移动或让终态 Attempt 回到 RUNNING。

## Trigger Ownership

新 Execution 的根触发来源为 MANUAL、SCHEDULE 或 AUTO_RECOVERY。RETRY 枚举存储值仅保留历史兼容，不是新 Execution 的正常创建来源。

例如 SCHEDULE / AUTO_RECOVERY Execution 的第二个 Attempt 仍保留原根 trigger。自动恢复与同根 Retry 的区别见 [Realtime Desired State](realtime-desired-state.md)。

## Execution Status

| 层级 | 活动状态 | 终态 |
| --- | --- | --- |
| Execution | PENDING、RUNNING、RETRY_WAITING | SUCCEEDED、FAILED、CANCELED、LOST |
| Attempt | PENDING、RUNNING | SUCCEEDED、FAILED、CANCELED、LOST |

RETRY_WAITING 属于 Execution，不属于已经 FAILED 的 Attempt。等待 backoff 仍占用该 Task 的活动执行位置，调度跳过、禁止下线 / 删除和取消入口必须覆盖它。

```text
PENDING → Attempt RUNNING
             ├─ 成功 → Execution SUCCEEDED
             ├─ 失败且可重试 → RETRY_WAITING → 下一 Attempt
             ├─ 失败且耗尽次数 → Execution FAILED
             └─ 用户取消 → Execution CANCELED

进程所有权丢失 → LOST
```

Execution 进入终态后不能自动追加 Attempt。连续 REALTIME Source 意外自然结束按失败处理，不把暂时无数据或异常结束当成功。

## Retry Policy

Retry Policy 由 `mode + maxAttempts + backoffSeconds` 组成。新建任务省略 Retry Policy 时，后端物化 `SMART + maxAttempts=3 + backoffSeconds=15`；历史 JSON 没有 mode、以及显式 API Policy 没有 mode 时按 `FIXED` 兼容，不改变历史重试语义。Task Editor 不暴露这些引擎参数；编辑请求省略时保留原 Task 策略。

`SMART` 不等于“所有异常都重试”。系统只对明确的瞬时失败进入 Retry：数据库连接 / recoverable / transient / timeout、SQLState 08（连接）与 40（事务回滚）、网络连接 / Socket timeout，以及 REALTIME 连续 Source 意外结束。SQL / Schema / 权限 / 完整性约束、产品校验、非法参数、能力不支持和未知异常直接失败。

写入安全优先于瞬时错误分类：OFFLINE APPEND / OVERWRITE 一旦 YakFlow Runtime 已启动，就视为可能存在已提交批次或已执行 TRUNCATE，SMART 不自动重放；如果失败发生在 Runtime 启动前，明确的瞬时规划 / 连接异常仍可重试。OFFLINE UPSERT 与 REALTIME CHANGELOG 依赖主键语义，允许对明确瞬时失败继续 SMART Retry，但仍不承诺 exactly-once。

SMART 退避以 `backoffSeconds` 为起点，按 Attempt 进行确定性倍增（15s → 30s → …），上限 300s；FIXED 保持原固定间隔。Durable Retry 继续持久化实际 `nextRetryTime`。

只有 FAILED 尝试进入通用 Retry 决策，SUCCEEDED / CANCELED / LOST 不自动重试。OFFLINE 与 REALTIME 共用生命周期；REALTIME Retry 复用既有 task/version CDC state。

v1.2 起，Backoff 的计划时间使用 Execution 已持久化的 `nextRetryTime`。运行进程内仍使用轻量等待，不使用 Quartz；但进程在 `RETRY_WAITING` 期间退出后，启动恢复会保留原 Execution Root，并从已持久化的 `currentAttempt / maxAttempts / backoffSeconds / nextRetryTime / definitionSnapshot` 恢复同一 Retry Chain。

启动恢复规则：

```text
PENDING / RUNNING
→ LocalExecution 所有权已经丢失
→ LOST

RETRY_WAITING
→ 保留原 executionId / root trigger / taskVersion / definitionSnapshot
→ 等到 nextRetryTime（已过期则立即继续）
→ 创建下一 Attempt
```

恢复时以已持久化 Attempt History 防止重复使用 attemptNo；如果等待重试记录缺少快照、计划时间、同步类型或已经没有剩余 Attempt，则该 Execution 收口为 LOST，而不是创建一个不受控的新根记录。

REALTIME desired-state 启动恢复在 Durable Retry 之后执行；保留的 `RETRY_WAITING` 仍属于 Active Execution，因此不会再创建重复的 `AUTO_RECOVERY` 根记录。

## Cancel Semantics

取消对象是整个 Execution，不是“跳过当前 Attempt 后继续 Retry”。PENDING / RETRY_WAITING 可以取消，后续等待不得再启动新 Attempt；RUNNING 通过本地注册表取消 Runtime，再收口 Execution / 当前活动 Attempt。单表启动先预留取消令牌、确认 Attempt RUNNING，再启动 YakFlow；规划过程发生取消时不得继续启动 Source / Sink。取消后等待工作线程真正退出再释放 CDC serverId，避免下一次恢复与旧 Runtime 重叠。

若 RUNNING Execution 的本地 Runtime 引用已经丢失，当前取消路径将其标记 LOST，而不是假报成功取消。对已终态 Execution 重复取消只返回历史记录；不会复活、追加尝试，也不会借此修改当前 Task 的运行意图。

## Definition Snapshot

快照保存稳定资源引用、任务类型 / 版本、表范围、写入方式和对应配置，不保存真实连接 JSON、密码、Token、SSH 私钥、CDC offsets、状态路径或 serverId 租约。

每次 Attempt 执行时按快照中的 datasource ID 安全解析当前连接并校验 Catalog。冻结 Task 输入不等于冻结外部数据、表结构或 Datasource 连接；不能据此承诺重试幂等或数据源变更安全。

## Metrics Semantics

- 单个 Runtime / Attempt 内计数保持单调；readRows 统计进入 Channel 的事件，writeRows 统计 SinkWriter.write 成功返回的事件，不是事务提交证明。
- Execution 只镜像当前或最终 Attempt 的计数。新 Attempt 开始时 readRows / writeRows 归零，因此同一个 Execution 跨 Attempt 可以下降。
- 不把多个 Attempt 相加为业务同步量；失败前可能已提交部分数据，重试可能重新读取或写入。
- REALTIME 计数是变更事件；一次 UPDATE 可以贡献前后两个事件，不直接等于 Source 表行数。

指标轮询和终态 flush 不应更改上述身份边界。多表 Root 运行期可将各表当前 / 最终 Attempt 指标缓存在所属 Worker 内，每次刷新直接写入镜像总和；最终 Root 终态使用数据库表级持久化值重新聚合。根实例和单表 Attempt 的周期性指标更新只在 RUNNING 状态生效，取消或完成后不再覆盖终态。详情通过 Attempt History 观察每次尝试，不伪造 checkpoint 时间或全局业务总量。

## Execution Event Log

Execution Detail 的“执行日志”数据源是产品生命周期事件，不是 Logback / JVM Server Log 的文件镜像。事件只记录有限的稳定语义，例如 Execution / Attempt 开始、Source / Target 执行计划准备、Attempt 成功 / 失败、等待重试、Execution 成功 / 失败 / 取消 / LOST，以及 REALTIME AUTO_RECOVERY 创建新 Execution。

事件按 Workspace + Execution 隔离，Attempt 级事件保存 attemptId，Execution 级事件不强制绑定 Attempt。展示消息进入持久化前统一脱敏并限制长度；不得写入连接 JSON、密码、Token、SSH 私钥、SQL Debug、逐批读写或其他高频 Runtime 明细。

`GET /api/v1/data-sync/instances/{id}/logs` 先验证当前 Workspace 对该 Execution 的可见性，再按 createTime / id 顺序返回事件。事件记录属于可观察性：写入失败会记录 Server Log，但不能把原本可成功的数据同步改判为失败。

v1.1.0 Release Migration 只从迁移生效后开始记录新的 Execution 产品事件，不回填历史 Execution。旧 Execution 因此可以返回空事件列表；不能用当前状态反推并伪造过去时间线。

## Write Safety

Retry 不改变 [OFFLINE 写入方式](README.md#offline-execution) 或 [YakFlow 写入语义](../yak-flow/README.md#jdbc-batch-connector)。APPEND 重放可能重复写；OVERWRITE 再次尝试会重新执行破坏性清空；UPSERT / CHANGELOG 的主键应用不构成端到端 exactly-once。

历史 / 显式 FIXED Policy 继续保持原配置；新建普通任务使用 SMART Policy。SMART 的安全边界由失败分类和写入模式共同决定，不能仅因为 maxAttempts=3 就解释成无条件自动执行三次。


## Shared Task Instance Persistence

本轮通过 [V8 Draft Migration](../../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V8__task_instance_schedule_alignment.sql) 将产品历史 Root Execution、Attempt、Event 原地迁移至通用 `yak_ops_task_instance`、`yak_ops_task_attempt`、`yak_ops_task_event`，ID、冻结定义版本和 Workspace 不变。Attempt / Event 的 `execution_id` 列改名为 `instance_id`，DATA_SYNC API 中现有 `executionId` 字段通过 DAO `@TableField` 映射保持兼容。历史多表 Route / TableExecution / TableAttempt 不变。

通用实例有 `task_type=DATA_SYNC`，以及未来 Workflow 可用、当前为空的 `workflow_instance_id` / `workflow_node_id`。Data Sync 历史恢复与取消操作必须限制 `DATA_SYNC` 类型，禁止更改其他插件的实例状态。新 Task Business 的只读 Instance/Attempt/Event/Metrics 不会制造新的实例或业务执行状态。

`yak_ops_task_attempt.log_uri` 是未来运行日志定位符，当前历史记录为空，不能认为事件日志等价于完整 Worker 文件。通用 Event 接口只返回结构化产品事件；安全的物理日志查看需要后续独立实现。见 [Task Instance Contract](../task-instance.md)。

## Persistence and Compatibility

[v1.1.0 Release Migration](../../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V2__v1_1_0.sql) 的 Execution Retry / Attempt section 保存 Task Retry Policy、Execution 的冻结策略 / 当前尝试 / 下次重试时间，以及 `yak_ops_data_sync_attempt`；Execution Product Event section 同时新增 `yak_ops_data_sync_execution_event`。

V8 前的 `yak_ops_data_sync_instance` 在 V8 后重命名为 `yak_ops_task_instance`，既有 Instance ID 保持不变。v1.1.0 以前的历史记录按单次执行解释；Migration 没有为历史 Instance 回填实体 Attempt 行，也不为历史 Execution 伪造产品事件，因此旧 attempts / logs 可以为空。

旧 Task 仍保留迁移期 maxAttempts=1、backoffSeconds=60；其 retryPolicy JSON 没有 mode 时读取为 FIXED。SMART mode 存在既有 JSON 字段中，不新增数据库列。Schema 由 DAO 维护，遵守 [Flyway Rules](../../../yak-ops-dao/FLYWAY_RULES.md)，不修改已冻结迁移或建立第二套 Task / Instance 模型；通用与 DATA_SYNC DAO 是同表投影。

## Operations Contract

列表一行对应一个 Execution，展示当前 / 最终 Attempt 信息；详情读取 `GET /api/v1/data-sync/instances/{id}/attempts` 获取尝试历史，并通过 `GET /api/v1/data-sync/instances/{id}/logs` 获取产品事件时间线。Retry Policy 的请求契约与默认 / 保留语义由后端维护；当前 Task Editor 不提供策略编辑。Attempt 观察、事件观察与系统策略是不同职责。

## Current Limits

SMART 提供有上限的确定性倍增退避，但仍不提供 jitter、Quartz Retry、分布式 Attempt ownership、跨节点接管或 exactly-once。Durable Retry 只恢复明确持久化的 `RETRY_WAITING`；已经进入 LOST / FAILED / CANCELED 的 Execution 不会被通用 Retry 复活。

当前仍是 single-node 恢复模型：没有 leader election、fencing、分布式 lease 或多实例竞争协调。

## Code and Verification

状态持久化：[DataSyncAttemptLifecycle](../../../yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/execution/lifecycle/DataSyncAttemptLifecycle.java) 与 [DataSyncInstanceRepositoryImpl](../../../yak-ops-dao/src/main/java/io/yak/ops/dao/repository/datasync/impl/DataSyncInstanceRepositoryImpl.java)。

验证入口：[DataSyncAutomationAcceptanceIT](../../../yak-ops-business/yak-ops-business-data-sync/src/test/java/io/yak/ops/business/datasync/impl/DataSyncAutomationAcceptanceIT.java) 及 [验证导航](README.md#code-and-verification)。重点是根身份 / 快照不变、次数与 backoff、取消阻断、RETRY_WAITING 跨进程恢复、活动集合及跨 Attempt 指标语义；执行结果绑定实际提交，不在这里记通过流水。
