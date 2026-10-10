# Data Sync Capability

Status: Task and history management active; YakFlow product execution integration pending.

**Current baseline:** OFFLINE/REALTIME single-table Task, publication, Quartz definition, operations queries and historical Instance/Attempt records are retained. The legacy synchronous execution engine has been removed, so manual run, scheduled execution and realtime recovery cannot submit a new Runtime job yet. Historical release evidence remains in `docs/release/`.

## Contract Map

| Concern | Reference |
| --- | --- |
| Task creation, publication and version semantics | [Task Lifecycle](task-lifecycle.md) |
| Historical multi-table definitions (retired from current editing) | [Multi-Table](multi-table.md) |
| Quartz scheduling and recovery | [Scheduler](scheduler.md) |
| Historical instance, Attempt, metrics and events | [Execution Retry / Attempt](execution-retry-attempt.md) |
| Realtime desired state | [Realtime Desired State](realtime-desired-state.md) |
| Core/Runtime/Connector | [YakFlow](../yak-flow/README.md) |

## Task Definition

[Generic Task Definition](../task-definition.md) owns Workspace, task name, taskType, publish status and executable definition version. The DATA_SYNC plugin extension owns exactly one source table and target table, Datasource IDs, sync type, write mode and retry/runtime policy. The same Task ID identifies both rows, with no duplicate shared-state writes. New/edited Tasks use Task fields only, not `tableRoutes[]`. Legacy Route rows remain untouched for historical inspection; stored multi-route Tasks are blocked from editing, publishing and running rather than silently reduced to the first route.


## Datasource Scope and Mapping

Datasource is the Workspace owner of credentials and the default connected database. Data Sync must not read Datasource DAO/Plugin Registry directly. It resolves a saved physical table through `DataSourceService.queryTableSchema(dataSourceId, path)`, which delegates to the sole `yak-flow-connector-jdbc` Catalog and dialect converter.

```text
SyncDefinitionService (DATA_SYNC definition + physical-schema validation)
       → DataSourceService (Workspace and credentials)
       → Datasource Plugin.openConnection (isolated driver / SSH)
       → YakFlow JdbcCatalogFactory / JdbcTableMetadata
       → Core TableSchema (ordered columns and PK)
       → JDBC Schema Compatibility (same-name target)
```

Product column rename/subset/reorder Mapping, Mapping Preview, automatic CREATE TABLE and DDL Preview were retired in PR #1555. No missing-target fallback is allowed. The target table must already exist and have compatible same-name columns; target-only non-nullable columns are rejected when defaults cannot be proven. REALTIME and OFFLINE UPSERT require matching complete source/target primary keys.

The JDBC Connector—not Task Business—owns physical column binding and dialect-specific logical types. The `JdbcSchemaMapper` and separate Data Sync `LogicalTable / LogicalColumn` models no longer exist.

## Schema / Logical Table

The active Schema is Core `TableSchema`, not an additional Product `LogicalTable`. For past v1.2 logical modeling proposals see historical release notes; do not infer implementation from those proposals.

The Connector Catalog also exposes native table/column descriptions (type name, JDBC type code, size, remarks and KEY_SEQ order) for the existing Datasource HTTP endpoints, without using native metadata for a second Business type conversion.

## Offline Execution

The product execution engine is not yet wired to the new YakFlow Runtime. Task definitions, Cron schedules and history can be managed; `runTask` and schedule fires fail explicitly rather than manufacturing successful instances.

`DataSyncWriteMode` remains a persisted product contract; before actual engine integration, supported execution modes, retries, overwrite semantics and connection lifetimes must be checked against the current JDBC Source/Sink contracts. Do not describe old Runtime behaviors as currently executable.

## Realtime Execution

MySQL CDC Connector and its engine-level acceptance are independent of Product REALTIME Task execution. Recovery of Product desired state does not silently create an engine job before wiring is complete. CDC data/state semantics belong to YakFlow.

## Compatibility and Historical Data

DATA_SYNC plugin fields for retired Mapping and Auto DDL remain in already-published database migrations and in the plugin extension for historical compatibility. They are cleared only by explicit task edit and must never silently be executed as same-name writes. Historical DefinitionSnapshot fields and published release documents remain readable without rewriting past facts.

The current version is at-least-once where Connector-specific tests prove it. Do not claim exactly-once or product end-to-end acceptance based only on individual Connector tests.

## Verification

Backend/Frontend/Distribution quality checks protect compilability. Connector JDBC/CDC manual acceptance verifies real MySQL, PostgreSQL and Oracle behavior; these are not substitutes for a future Product E2E after Runtime integration.
