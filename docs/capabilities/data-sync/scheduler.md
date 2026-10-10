# Data Sync Scheduler Contract

Status: Active

Scope: OFFLINE Schedule 定义、业务触发与 Quartz 运行时边界。

## Goal

Yak Ops 决定任务能否运行，Quartz 只决定何时到点。ScheduleEngine 是框架无关接口；Quartz 类型不得进入 Business Contract、DTO / VO、DAO Entity 或 YakFlow。

## Schedule Definition

同一 Workspace / OFFLINE Task 最多一个 Schedule，`yak_ops_data_sync_schedule` 是定义和 enabled 状态的持久化来源。Quartz 当前使用 RAMJobStore，Trigger 不是业务事实来源。

ScheduleEngine 接收 scheduleId、workspaceId、taskId、cronExpression 和显式 timeZone。JobData 仅存三个稳定 ID；不保存 Task JSON、连接信息、凭证、映射或 CDC state。

Cron 使用 Quartz 语义，时区由后端 ZoneId 校验，不能依赖 JVM、宿主机或浏览器默认值。引擎接口提供校验、注册、修改、移除、next-fire 查询和未来触发时间预览，不负责授权、并发、实例生命周期或 Retry。预览必须复用 Quartz CronExpression + 显式 ZoneId，不能在前端或 Business 层用另一套 Cron 解释器估算。

## Command Semantics

以下路径相对于 `/api/v1/data-sync/tasks/{id}/schedule`：

| 命令 | 当前语义 |
| --- | --- |
| PUT | 保存 Cron / Time Zone；新 Schedule 默认 disabled；已有记录保留 enabled 状态 |
| GET | 读取已有 Schedule 定义；未配置 Schedule 时成功返回空数据，不生成虚构默认记录，也不把手工任务视为异常 |
| POST /enable | 要求 OFFLINE + PUBLISHED 且 Schedule 已存在；校验后启用并注册 Trigger |
| POST /disable | 标记 disabled，提交后移除 Trigger；不取消已创建的 Execution |

独立预览接口：

```text
POST /api/v1/data-sync/schedules/preview
```

输入未持久化的 Cron + Time Zone，固定返回未来 5 次触发时间；不要求 Task 已创建，也不创建或修改 Schedule / Quartz Trigger。
Schedule 定义可独立于 Task 发布状态保存；已有启用记录修改后在 commit 后更新 Runtime，并要求 Task 保持已发布。保存不会隐式启用一个未启用的 Schedule，也不会增加 Task definitionVersion。

Cron 不能为空。当前没有通过空 Cron 删除既有 Schedule 的语义；不要把前端“未配置”当成后端删除命令。Stop Execution 不代表停用以后到点触发的 Schedule。

## Fire Boundary

```text
Quartz Job（稳定 ID + scheduledFireTime）
  → DataSyncScheduleFireListener
  → 重读 Schedule，确认 enabled 且 taskId 匹配
  → 重读 Task，确认 OFFLINE + PUBLISHED
  → 检查活动 Execution
       ├─ 存在 → SKIP，不建并发根记录
       └─ 不存在 → 校验资源 / 映射 → 新 SCHEDULE Execution
```

活动状态统一使用 [Execution Status](execution-retry-attempt.md#execution-status)，包括 RETRY_WAITING。JobData 不是执行授权来源；不能用 Quartz `@DisallowConcurrentExecution` 代替业务校验。

当前策略固定 SKIP_IF_RUNNING，不提供 QUEUE / PARALLEL，也不把 skipped fire 自动变成补跑请求。Retry 在 Execution 内发生，不通过 Quartz refire 或新的 Cron Execution 实现。

## Misfire and Recovery

Misfire 固定 DO_NOTHING。进程错过的计划时间不会在恢复后批量补跑。

启动从 DB 读取 enabled Schedule，校验关联 Task 和 Cron 后重新注册。成功下线在业务事务内关闭 Schedule，提交后移除 Trigger；删除 Task 同步删除其 Schedule 业务记录，提交后清理 Runtime。

DB 提交与 Quartz 注册不是原子事务。commit 后注册 / 移除失败可以留下定义与 Runtime 暂时不一致；不能承诺请求失败必然回滚已提交定义。启动重建也不是常驻自动修复循环；无效启用记录的恢复异常不能描述成逐条忽略。

## Runtime Observation

运维读模型查询 Quartz 的 nextFireTime，并按 Schedule timeZone 转换展示。未启用、Trigger 不存在或查询失败时可能没有该值，前端不能自行推算一个时间代替 Runtime 事实。普通 Schedule GET 不保证携带运行时 nextFireTime。

## UI Ownership

OFFLINE 编辑器保存 Schedule 定义；OFFLINE Task list 负责显式 Enable / Disable；运维中心只负责跨 Task 运行态观察。编辑器使用 Yak UI Cron Scheduler Picker 生成/保留 Quartz Cron，Time Zone 使用选择控件并默认 Asia/Shanghai；Picker 内的未来 5 次时间通过后端 Preview API 获取。保存顺序为 Task → Schedule，Save & Publish 为 Task → Schedule → Publish。保存/上线不隐式启动 Schedule，用户从 Task list 明确启动。跨 HTTP 调用不是一个原子事务，部分失败不能展示整体成功。

从未创建 Schedule 且编辑器 Cron 留空时维持手动运行；保存 Schedule 不等于启用。任务详情和编辑器读取这类任务时必须把 Schedule 缺失解释为“仅手动”，不能因为没有 Schedule 阻断页面加载。Task 下线继续自动 disable Schedule；重新上线不会自动恢复 enable，需要在 Task list 再次显式启动。具体控件与页面布局由前端 owner 维护，不在此复制。

## Persistence

Schema 位于 [v1.1.0 Release Migration](../../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V2__v1_1_0.sql) 的 Offline Schedule section。它是现行持久化，不再是待实现目标。Draft / Release Migration 生命周期和冻结规则见 [Flyway Rules](../../../yak-ops-dao/FLYWAY_RULES.md)。

没有启用 Quartz JDBC JobStore 或集群。后续采用 JobStore 时仍不能把 QRTZ 表当业务数据库；生产环境不得通过 initialize-schema=always 自动重建，Schema 必须由版本迁移管理。

## Code and Verification

[DataSyncScheduleServiceImpl](../../../yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/impl/DataSyncScheduleServiceImpl.java) 拥有保存、启停、onFire、commit 后 Runtime 更新和启动重建；[QuartzScheduleEngine](../../../yak-ops-boot/src/main/java/io/yak/ops/boot/scheduler/QuartzScheduleEngine.java) 拥有 Cron、时区、Misfire 与 next-fire。

[Backend Acceptance](../../../.github/workflows/backend-acceptance.yml) 执行 QuartzScheduleEngineTest 与 DataSyncAutomationAcceptanceIT；业务替身测试不代替真实 Quartz 计时语义。[手工 Automation E2E](../../e2e/data-sync/automation/README.md) 验证产品链路。这里定义方法与边界，不记录某次通过结论。
