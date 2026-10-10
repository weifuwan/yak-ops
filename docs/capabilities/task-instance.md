# Task Instance, Attempt, Schedule and Metrics Contract

Status: Active — shared persistence and read-only Task APIs; runtime execution and distributed orchestration remain unimplemented.

## Stable Identity

The single source of truth for product executions is `yak_ops_task_instance`, not a second DATA_SYNC or Workflow-specific instance table. One manually started, scheduled or eventually workflow-triggered Task creates exactly one Task Instance ID. Retries create Attempt records under that same Instance and never create a second root Instance. Instance contains the task definition ID/version, task type, launch trigger, status and snapshot; it does not represent YakFlow's internal ExecutionGraph.

```text
TaskDefinition (one stable ID)
  ├─ TaskInstance A (standalone; workflowInstanceId=NULL)
  │    ├─ TaskAttempt 1
  │    └─ TaskAttempt 2
  └─ TaskInstance B (future Workflow; workflowInstanceId / workflowNodeId)
       └─ TaskAttempt 1
```

A future Workflow must reference this Task Instance rather than duplicating it. `workflow_instance_id`, `workflow_node_id`, `schedule_id` and `scheduled_fire_time` are nullable provenance fields; PR3 does not submit Workflow nodes or create Schedule-triggered executions.

The historical numeric status codes remain unchanged, including Instance `RETRY_WAITING=7`, which is **active**. Attempt starts at 1. DATA_SYNC's `sync_type`, `read_rows` and `write_rows` remain DATA_SYNC-specific columns/metrics, not universal Task obligations. A non-DATA_SYNC future Task may have null `sync_type`.

## Persistence & Migration

[V8 draft migration](../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V8__task_instance_schedule_alignment.sql) renames existing tables in place, without copying rows or allocating replacement IDs:

| Current table | Previous table |
| --- | --- |
| `yak_ops_task_instance` | `yak_ops_data_sync_instance` |
| `yak_ops_task_attempt` | `yak_ops_data_sync_attempt` |
| `yak_ops_task_event` | `yak_ops_data_sync_execution_event` |
| `yak_ops_schedule` | `yak_ops_data_sync_schedule` |

Attempt/Event `execution_id` becomes `instance_id`, Schedule `task_id` becomes `target_id`. Existing DATA_SYNC Java DTO/VO `executionId` and `taskId` remain backward compatible through explicit MyBatis column mappings. Historical TableExecution, TableAttempt and Route tables remain read-only and retain their root Execution IDs. Released migrations never change.

DATA_SYNC-specific DAO models still read/write these shared physical tables. Generic `io.yak.ops.dao.entity.task` models are *read-only projections of the very same tables*, not second persistence owners.

## Schedule Targets

`yak_ops_schedule` persists `target_type` and `target_id` as the unique target identity. Existing schedules are migrated to `target_type=TASK` with their original Task IDs. Unique constraint: `(workspace_id, target_type, target_id)`. The `WORKFLOW` type is reserved for a future implemented workflow scheduler; it does not imply runnable workflow support today.

`DataSyncScheduleService` remains the only currently implemented schedule edit/enable/disable/Quartz bridge. Its queries and startup restore strictly select `target_type=TASK`; it does not accidentally read future WORKFLOW schedules. Cron, ZoneId, misfire DO_NOTHING and post-commit registration remain unchanged. Generic `ScheduleService` currently provides Workspace-scoped read-only Task Schedule queries and does not claim Quartz trigger availability.

## Metrics & Events vs Logs

Generic metrics count *root Task Instances* from `yak_ops_task_instance` within a bounded Workspace/type/time window, never summed Attempts. `DATA_SYNC` read/write rows and trend metrics continue through its existing specialized aggregate DAO, strictly filtered to `task_type=DATA_SYNC`. A retry may repeat records; read/write rows are not committed-row proof.

`yak_ops_task_event` stores **structured product lifecycle events**, not full worker/JVM log output. `yak_ops_task_attempt.log_uri` reserves a controlled per-Attempt log reference; historical entries are NULL. No URI is returned to ordinary clients; generic Attempt responses only include a `logAvailable` flag, which is not proof the file is retrievable. Secure physical log reads/downloads need their own backend protocol and authorization; no fake log or Worker location is created in this PR.

## HTTP & Rollout

Generic Task APIs (all require a trusted Workspace context):
- `GET /api/v1/task-instances/{id}`: shared Instance view without raw configuration
- `POST /api/v1/task-instances/page`: paginated shared Instance history
- `GET /api/v1/task-instances/{id}/attempts`: Attempt history, safe `logAvailable` boolean
- `GET /api/v1/task-instances/{id}/events`: structured product events
- `GET /api/v1/task-schedules/tasks/{id}`: persisted Task Schedule or null if unconfigured
- `GET /api/v1/task-metrics/summary?days=7&taskType=DATA_SYNC`: root Instance metrics

DATA_SYNC's existing `/api/v1/data-sync/**` URLs, response shapes, Workspace isolation, Quartz and legacy cancellation behavior remain preserved. Its historical `/instances/{id}/logs` endpoint still returns **product events**, not execution log files. Legacy Trace endpoints still explicitly fail when the removed Runtime is unavailable.

The shared Task business module is currently **read-only** for Instances, Schedules, Metrics and Event logs. Instance creation/stop, distributed Worker assignment, real logging, durable Workflow orchestration and YakFlow submission are **not** part of this milestone. Do not interpret a stored `log_uri` or discovered task type as a functioning executor.
