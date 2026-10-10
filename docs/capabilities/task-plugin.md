# Task Plugin Contract

Status: Active — discovery and parameter validation only

## Goal and Ownership

A Task is a platform-level definition that can eventually run independently or as a Workflow node. Task Plugin is the extension seam for the configuration and execution behavior of one task type. This contract currently implements only type discovery, parameter parsing and local validation; common TaskDefinition, TaskInstance and Workflow persistence are not implemented by this contract.

- [Task Plugin API](../../yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-api/src/main/java/io/yak/ops/plugin/task/api/TaskPlugin.java): plugin-owned parsing
- [Task Plugin Factory](../../yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-api/src/main/java/io/yak/ops/plugin/task/api/TaskPluginFactory.java): ServiceLoader registration
- [Task Plugin Registry](../../yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-api/src/main/java/io/yak/ops/plugin/task/api/TaskPluginRegistry.java): type resolution and parse + validate
- [DATA_SYNC parameters](../../yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/main/java/io/yak/ops/plugin/task/datasync/SyncParameters.java): single-table task-specific configuration

## Active Contract

```text
Boot (TaskPluginRegistry bean)
  -> ServiceLoader<TaskPluginFactory>
    -> DATA_SYNC / SyncPluginFactory
      -> SyncPlugin.parseParameters(json)
      -> SyncParameters.validate()
```

Task types are stable, case-insensitive strings with canonical uppercase underscore spelling; unknown/duplicate types fail closed. Each plugin factory and plugin must report the same type. The registry does not silently select a default plugin.

`DATA_SYNC` has one configuration type for both `OFFLINE` and `REALTIME`. It validates a required sync type, write mode, source/target Datasource IDs, and physical table names. Optional database/schema paths and runtime/realtime/retry JSON objects are supported. Retired multi-table routes and unrecognized fields are not supported. Plugin parameter errors do not echo the submitted JSON.

This is **local configuration validation only**. Datasource permissions and physical schema compatibility are still performed by the existing Data Sync Business layer; secrets remain with Datasource. No Task plugin persists a product instance or manages an execution retry.

## Execution Boundary

A future Task execution adapter may submit DATA_SYNC through YakFlow's existing `PipelineExecutor` / `JobClient`. YakFlow's StreamGraph / JobGraph / ExecutionGraph and StreamTask refer to **data-plane execution**; they must not be reused as Workflow DAG and Workflow TaskInstance models.

The current embedded YakFlow runtime is not wired into Data Sync product endpoints. Plugin discovery and a validated task payload do not enable Run/Start. Standalone and Workflow-triggered task execution must converge on one eventual TaskInstance/Attempt lifecycle, rather than recording two product instances.

## Planned, Not Implemented

- Unified TaskDefinition / versioning and migration of existing data-sync definitions
- General TaskInstance / Attempt / Log / Metrics / Schedule lifecycle
- WorkflowDefinition, Workflow nodes/relations, WorkflowInstance or distributed Master/Worker scheduling
- DATA_SYNC execution adapter and YakFlow Runtime integration

Do not add tables, HTTP endpoints or runtime execution claims to this SPI-only milestone. Migration and execution need independent acceptance contracts and tests.
