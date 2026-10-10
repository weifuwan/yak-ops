# Data Sync Rules

Status: Active

Scope: `yak-ops-business/yak-ops-business-data-sync/**` and matching Common/DAO contracts.

## Business Service Ownership

- `SyncDefinitionService`: shared OFFLINE/REALTIME Task CRUD, publication, definition version and single-table Source/Target definition.
- `DataSyncScheduleService`: Cron validation, saved Schedule lifecycle, Quartz registration after commit, startup registration and the `DataSyncScheduleFireListener` boundary.
- `DataSyncInstanceService`: existing persisted Execution/Attempt/Event history, instance queries and current cancel status handling. Manual Run explicitly rejects when the new Runtime is not connected.
- `DataSyncOperationsService`: operations task read model, aggregate metrics, time buckets, failure ranking and Quartz next-fire observation.
- The split Definition and DataSync Controllers inject only their own relevant Services; do not recreate a giant `DataSyncService` façade or cyclic Service dependencies.

## Single-Table Contract

DATA_SYNC source/target fields in `yak_ops_data_sync_task` are the sole editable plugin configuration for both sync types. Reject incoming `tableRoutes` requests explicitly. `DataSyncTableRouteDefinitionService`, dual writes and route-order reconciliation have been retired.
Historical Route table/Repository and frozen Execution snapshots stay in storage for compatibility and read-only historical access. Detect persisted multi-route Tasks and block edit, publish and Run; never silently select the first historical route as the complete Task. Do not rewrite released Flyway migrations.

- `definitionVersion` changes only when executable Task fields or normalized runtime/retry policies change; publication and metadata-only changes leave it intact.
- Editing requires an UNPUBLISHED Task; publishing revalidates Catalog Schema; unpublishing requires no active instance and disables Schedule; deleting preserves historical executions.
- Source/Target Datasource are Workspace-scoped; physical Schema discovery runs through `DataSourceService` and YakFlow JDBC Catalog. Business never introduces its own JDBC dialect/type conversion.
- The target table must exist. Validate same-name compatible columns; realtime / UPSERT require matching complete primary keys. Mapping and automatic table creation are retired.
- OFFLINE and REALTIME share Task CRUD and lifecycle. Different write-mode/config/datasource validations do not justify duplicate Task Services.

## Canonical Definition Persistence

The generic `yak_ops_task_definition` owns shared task ID, Workspace, name, status, executable version and remark; the DATA_SYNC extension owns source/target and runtime policies. `SyncDefinitionRepository` persists both within the existing Business transaction and appends immutable versions only when executable configuration changes. No duplicate shared-column writes, hidden global Workspace fallback or re-created historical versions. See [Task Definition](../../docs/capabilities/task-definition.md).

## Scheduler / Instance / Operations

- The Schedule service and task-status mutations have no dependency cycle; Task invokes Schedule disable/delete inside the current transaction.
- Update/unschedule Quartz only after DB commit; Quartz and DB commits are not atomic. `onFire` re-reads Schedule and Task within the correct Workspace before considering execution.
- The old business execution engine is removed. Run and scheduled Fire must reject without creating a fabricated Execution. No new checkpoint, CDC auto-recovery, executor or durable retry logic is introduced here.
- Historical instances, attempts, table executions, events and snapshots remain visible; cancellation retains its existing persisted status-transition behavior. Never treat historical RUNNING as a live YakFlow process.
- Dashboard queries must be workspace-scoped, bound to allowed ranges and use dedicated `DataSyncOperationsMetricsRepository` aggregation. Fill missing time buckets and avoid Attempt-based double counting.

## Verification

Use existing Java rules and relevant Contract Tests. Preserve controller endpoints and response semantics, Quartz timing checks, read-only legacy history, and the single-table editor's create/edit flow. Never report Runtime E2E success from business stub tests.
