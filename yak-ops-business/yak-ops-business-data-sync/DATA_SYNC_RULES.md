# Data Sync Rules

Status: Interface-only transition

Scope: `yak-ops-business/yak-ops-business-data-sync/**`。

当前只保留 `DataSyncService`、`ScheduleEngine`、`DataSyncScheduleFireListener` 接口及必要的 Schedule 契约类型。旧业务编排、执行器、生命周期管理、恢复、调度实现和测试已删除，不提供 Spring Bean 实现。

任务 DTO / VO 和已发布 Flyway Migration 保留，但旧 DAO Entity / Mapper / Repository 代码已清理；它们的存在不表示当前分支可以执行任务。不得恢复旧 `LocalExecution` 依赖。

前端任务页面和 Data Sync HTTP Controller 暂时不提供；新逻辑留到后续 PR，不在本轮添加占位实现。产品历史行为只以 [发布材料](../../docs/release/README.md) 为证据，当前状态见 [Data Sync Capability](../../docs/capabilities/data-sync/README.md)。
