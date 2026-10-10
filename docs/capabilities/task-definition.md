# Task Definition Contract

Status: Active — generic Definition identity, Workspace-scoped reads and immutable version snapshots; DATA_SYNC is the first migrated task type.

## Ownership

`yak_ops_task_definition` holds the sole writable source for Task ID, Workspace ID, name, plugin `task_type`, publication status, current `definition_version`, remark and audit fields. Types are stable open strings (`DATA_SYNC` today), not an enum of all possible plugins.

`yak_ops_data_sync_task` remains the indexed DATA_SYNC-only configuration table, keyed by the same stable Task ID and Workspace ID. It owns OFFLINE/REALTIME mode, source and target database/table, write and retry policy, and realtime `desired_state`. Shared name/status/version/remark columns were migrated out; new tasks **do not** dual-write these fields. This avoids hiding indexed Datasource filters inside an unqueryable generic JSON blob.

New definitions are added inside the existing Data Sync Business transaction. Plugin-specific physical Schema and target compatibility validation remains in `SyncDefinitionServiceImpl / SyncDefinitionValidator`. The generic `DefinitionService` currently provides read-only current-definition and version queries; general cross-plugin creation and execution dispatch are not available.

## Version Identity

A Data Sync task retains its original Task ID. An executable parameter change increments `definitionVersion` and transactionally appends an immutable row in `yak_ops_task_definition_version`. Rename, remark and publication status do not advance its executable version.

Versions use `(workspace_id, definition_id, version)` uniqueness. Current DATA_SYNC parameters are snapshotted with source/target resource IDs and table names, write mode, and runtime/retry policy JSON; datasource passwords and SSH secrets are never serialized.

Migration [V7](../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V7__task_definition.sql) backfills one record for the **currently stored version** of each existing DATA_SYNC definition and preserves existing Task IDs, Schedule task references and historical Instance/Attempt IDs. Older configuration revisions not stored in legacy tables cannot be reconstructed and must not be invented. All published V1–V3 migrations remain unchanged.

Deleting an unpublished Definition retains its historical version rows and existing execution records. No WorkflowInstance or TaskInstance schema is introduced by this change.

## HTTP Surface

- `GET /api/v1/task-definitions/{id}`: current generic identity/status/version
- `GET /api/v1/task-definitions/{id}/versions`: actually persisted historical versions
- Existing `/api/v1/data-sync/tasks/**` CRUD and publish/unpublish routes remain supported by the new Data Sync `DefinitionController`. There is no second writable Task Definition HTTP API.

Workspace authorization is established by Boot, and the Service/DAO apply Workspace scoping on every definition/version read. Existing Data Sync DTO / VO JSON and frontend screens remain unchanged.

## Execution Boundary

This PR owns only Task Definition persistence. Current instance, Attempt, Schedule, Dashboard and cancellation semantics are not migrated here. Manual Run and effective Cron fire still fail explicitly until the Data Sync → YakFlow Runtime bridge is implemented; having a Definition row or a discovered plugin is not executable proof.

Later Workflow nodes must reference stable Definition ID plus version. They must not recreate DATA_SYNC-specific definitions or Product TaskInstances.
