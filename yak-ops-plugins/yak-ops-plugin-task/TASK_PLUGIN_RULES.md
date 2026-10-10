# Task Plugin Rules

Status: Active

Scope: `yak-ops-plugins/yak-ops-plugin-task/**`

Depends On: [Architecture](../../ARCHITECTURE.md), [Java Rules](../../JAVA_RULES.md), [Task Plugin Contract](../../docs/capabilities/task-plugin.md).

## Ownership

- `yak-ops-plugin-task-api`: provider-neutral plugin interface, immutable registration, type resolution and parse/validate entry.
- `yak-ops-plugin-task-data-sync`: the `DATA_SYNC` task type, its single-table JSON parameters and local validation.
- `yak-ops-plugin-task-all`: Maven dependency aggregation only; do not register plugins a second time.
- Boot owns wiring the registry into the application. The Business layer will own TaskDefinition/TaskInstance/Schedule persistence and lifecycle, not these plugins.

## Stable Type Contract

Task types are an open string set. Canonical names use uppercase underscore notation; lookup accepts case-insensitive, hyphen/underscore spelling. Do not introduce a global TaskType enum. Register a `TaskPluginFactory` via `META-INF/services`, and require its type to match the created `TaskPlugin`. Duplicate types, unknown types and mismatched factories fail closed.

The registry is constructed with `ServiceLoader` and can be supplied with factories for tests. Plugin parameter parsing runs through `TaskPluginRegistry.parseParameters()`, which always invokes `TaskParameters.validate()`. Plugin errors must not expose raw JSON, secrets or connection strings.

## DATA_SYNC Contract

- `DATA_SYNC` is a single plugin type, with `OFFLINE` / `REALTIME` declared in its parameters. Do not split it into two plugins.
- `SyncParameters` has only plugin-owned source/target table paths, runtime config, and retry-policy shape. Common Task identity, name, status and version are not plugin fields.
- This version validates required IDs/table names, string lengths, enum modes, and optional configuration JSON object shapes. Realtime only accepts `APPEND` write mode. Unknown fields, including retired `tableRoutes`, are rejected.
- Workspace authorization, Datasource metadata, Schema compatibility, executable runtime policy and persisted Task lifecycle are **not** part of local SPI validation.
- Do not depend on Data Sync Business, DAO, Boot or YakFlow Runtime from the Task Plugin API.
- Do not add an independent scheduler, job runner, product execution lifecycle, checkpoint manager or logging persistence to plugin modules.

## Current Rollout Boundary

The registry is discoverable at Boot startup, but existing Data Sync HTTP/Service/DAO paths are unchanged and do not call it yet. No TaskDefinition/TaskInstance migration, new runtime submission or workflow execution has been added. A successfully validated plugin configuration is **not** proof that its task can execute.

## Verification

Run Java 21 Maven compilation, ordinary tests and repository Spotless check. Contract tests must include SPI discovery, type collision, unknown type, valid OFFLINE/REALTIME configurations, and malformed/unsupported parameters. Data Sync runtime E2E belongs to the subsequent execution-wiring change, not to this plugin registration contract.
