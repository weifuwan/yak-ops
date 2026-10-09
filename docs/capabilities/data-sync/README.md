# Data Sync Capability

Status: Interface only — offline/realtime implementations removed

Scope: Data Sync 的产品协议及历史版本导航。

当前分支仅保留 `DataSyncService`、`ScheduleEngine`、`DataSyncScheduleFireListener` 等接口和必要的 DTO / VO、历史数据库 Migration。离线任务、实时任务、多表任务、调度、自动恢复、指标、执行日志、Controller 和前端任务页面均没有运行实现，不能对外宣称可用。

`yak-ops-core` 保留通用 Source / Sink / Operator / Pipeline 接口；`yak-flow-runtime` 保留新的 Core-based 通用运行框架，但尚未接入 JDBC / CDC Connector 或产品业务入口。

旧运行路径、Executor 和 Connector 实现已经移除；后续开发必须从 Core 契约出发，不重新引入旧的 `yak-flow-api.source/sink` 或 Runtime 根包 Execution 类型。

已发布版本的范围、能力和人工验收记录以 [历史发布材料](../../release/README.md) 和 [历史 E2E 文档](../../e2e/data-sync/README.md) 为准；这些材料不代表当前开发分支仍具备相同功能。
