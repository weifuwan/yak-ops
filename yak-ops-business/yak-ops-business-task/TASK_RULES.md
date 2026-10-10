# Task Business Rules

Status: Active — Definition plus shared Task history, Schedule and Metrics read models

Scope: `yak-ops-business/yak-ops-business-task/**`

Depends On: [Business Rules](../BUSINESS_RULES.md), [Task Definition Contract](../../docs/capabilities/task-definition.md).

`definition.DefinitionService` is the generic read-only Business interface for current Task Definition and persisted version history. Boot only injects this interface, not DAO or concrete plugin classes. Queries must resolve a trusted `WorkspaceContext` and use workspace-scoped Repository methods. Not found or cross-workspace objects return the same failure, without exposing other workspace data.

`SyncDefinitionService` remains the single authoritative DATA_SYNC-specific edit/publish orchestrator, including DataSource schema validation and the current front-end's DTO/VO shape. Do not add generic CRUD that creates orphan plugin configurations, a second writable definition store, or a duplicate execution lifecycle. Future task-type writes require an explicit plugin configuration persistence/validation contract.

A Task Definition version is immutable after creation. The DAO version repository supports only append and read; historical version IDs, snapshots and root Instance references survive DATA_SYNC task deletion.

Generic `instance.InstanceService`, `log.LogService`, `schedule.ScheduleService` and `metrics.MetricsService` are read-only and use the existing shared Task tables. No generic write API is exposed; DATA_SYNC still owns its historical lifecycle commands. Each Instance/Attempt/Event query validates the current Workspace and checks parent Instance visibility before showing children. Never include the raw definition snapshot or `log_uri` in HTTP responses.

Generic Metrics use a bounded 1–31-day window and aggregate root Instance rows, not Attempt histories. DATA_SYNC-specific rows and trends remain in the DATA_SYNC metrics read model. The common Schedule query checks the Task Definition Workspace; WORKFLOW schedule execution is not active.

No Task business class invokes YakFlow JobClient, Quartz, MyBatis Mapper or another domain's concrete Service. See [Task Instance Contract](../../docs/capabilities/task-instance.md).
