# Task Business Rules

Status: Active — Definition query and version history

Scope: `yak-ops-business/yak-ops-business-task/**`

Depends On: [Business Rules](../BUSINESS_RULES.md), [Task Definition Contract](../../docs/capabilities/task-definition.md).

`definition.DefinitionService` is the generic read-only Business interface for current Task Definition and persisted version history. Boot only injects this interface, not DAO or concrete plugin classes. Queries must resolve a trusted `WorkspaceContext` and use workspace-scoped Repository methods. Not found or cross-workspace objects return the same failure, without exposing other workspace data.

`SyncDefinitionService` remains the single authoritative DATA_SYNC-specific edit/publish orchestrator, including DataSource schema validation and the current front-end's DTO/VO shape. Do not add generic CRUD that creates orphan plugin configurations, a second writable definition store, or a duplicate execution lifecycle. Future task-type writes require an explicit plugin configuration persistence/validation contract.

A Task Definition version is immutable after creation. The DAO version repository supports only append and read; historical version IDs, snapshots and root Instance references survive DATA_SYNC task deletion.

No Task business class invokes YakFlow JobClient, Quartz, MyBatis Mapper or another domain's concrete Service.
