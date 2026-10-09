# Data Sync Capability

Status: Management Restored — Execution Engine Unavailable

本分支保留离线 / 实时任务的前端页面、路由、Controller、Service、DAO 和数据库表；**尚不能执行数据同步**。

## 保留

- 任务 CRUD、发布 / 下线、多表路由、Schema / 字段映射预览。
- Cron 调度配置的保存、读取和预览，运维指标、历史执行实例 / Attempt / Trace 查询。
- Data Sync Entity / Mapper / Repository、已有 Flyway Migration、DTO / VO；现有数据库结构不删除或回滚。
- JDBC Schema / Catalog / Dialect / DDL 预览辅助代码、诊断 Trace 数据契约。
- `yak-ops-core` 和新的 Core-based 通用 Runtime 框架，供后续重新接入。

## 暂不可用

- 旧 `LocalExecutionEngine` / `LocalExecution`、JDBC / CDC Source、Reader、Sink、Debezium 及业务 `execution.executor` 继续删除。
- `runTask`、`enableSchedule` 返回 `ENGINE_UNAVAILABLE (42023)`；不创建无法执行的 `PENDING` 实例，也不会注册新 Cron 触发器。
- 历史调度配置、Desired State 和执行记录继续保留，但缺少引擎时不会自动运行或恢复同步。历史 `RUNNING` 实例只能标为 `LOST`，不能假装运行时已被取消。

后续实现必须使用 `yak-ops-core` 统一 Source / Sink / Pipeline 契约，不把旧 `yak-flow-api.source/sink` 和 Runtime 根包执行器引回来。

已发布版本与验收记录保持原样，见 [Release](../../release/README.md) 和 [历史 E2E](../../e2e/data-sync/README.md)；不代表当前分支仍具备数据同步执行能力。
