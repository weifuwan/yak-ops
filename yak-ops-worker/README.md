# Yak Ops Worker

DolphinScheduler `dolphinscheduler-task-executor` and
`dolphinscheduler-worker/executor` are the upstream reference for the
TaskEngine → Container → TaskExecutorWorker → PhysicalTaskExecutor architecture.

The first implementation is embedded: one JVM, one Master, a configurable
number of exclusive task slots, no RPC, no Worker registry. The Yak Ops Task
SPI and its deferred TaskHandle lifecycle remain authoritative for physical
execution. The platform persists the Task Instance and does not release
execution claims on an unconfirmed Job result.

Source alignment: Apache DolphinScheduler dev
`ee47c371a7f920b0b5bbba3fb01dd1695f5800a3`. Imported upstream files
retain their ASF headers; adapted files are documented in this module.

## Source-aligned execution (PR #1813 follow-up)

- `TaskEngine`, `TaskEngineBuilder`, `AbstractTaskExecutor`,
  `AbstractTaskExecutorContainer`, and `TaskExecutorEventBusCoordinator`:
  reconstructed from DolphinScheduler `ee47c371` with matching dispatch,
  initialization, worker thread, event dispatch and finalization structure.
- `PhysicalTaskExecutorBuilder`, `PhysicalTaskExecutorFactory`, and the
  concrete `PhysicalTaskPluginFactory` use the upstream construction and
  `createPhysicalTask` / `getTaskChannel` model.
- Yak Ops differences are deliberate: string Task Instance ID, existing
  `ExecutableTaskPlugin` / deferred `TaskHandle`, Master-side durable
  claim and authorization, a local rather than RPC lifecycle ACK, and no
  remote log upload or worker filesystem staging.
- The runtime context is transportable **data**, not a holder of factories or
  callbacks. Workflow/project fields stay nullable until a Workflow model
  supplies authoritative values. No invented Workflow identifiers.
- A Physical Task does not relinquish ownership on unconfirmed async engine
  completion; the business `RECONCILING` state and dispatch token remain
  authoritative. Run and inspect all backend checks before merging.

## Registry and resource scheduling follow-up

The embedded Worker still uses the Yak Ops singleton owner table and the
per-resource exclusive Claim table. Neither has a one-to-one table equivalent
in DolphinScheduler. The non-heartbeating `last_seen_time` column and
unused MyBatis Owner entity/mapper were removed rather than presenting them
as a DolphinScheduler-style node registry.

Replacing Owner requires the **whole** DolphinScheduler Registry lifecycle
(including JDBC Registry data, lock, client heartbeat and change-event tables),
with recovery fencing before a second process can launch tasks. Replacing
Claim with DolphinScheduler TaskGroup/TaskGroupQueue also requires an explicit
decision about multi-target atomic exclusivity and MySQL CDC replication
server-ID collisions: a Task Group slot is not equivalent to a per-target
lock. Do not remove either live guard until those contracts have parity.

## DolphinScheduler Task Group slot migration

The old `yak_ops_task_execution_claim` table is replaced by `t_ds_task_group`
and `t_ds_task_group_queue`. The SQL slot CAS follows DolphinScheduler's
`TaskGroupMapper.xml` `acquireTaskGroupSlot` / `releaseTaskGroupSlot`.
Yak Ops uses a Workspace-scoped Task Group of capacity one per canonical
exclusive resource and permits one Task Instance to reserve multiple groups
in a transaction, sorted by resource identity. Queue `task_id` uses the Yak
Ops string Task Instance ID. Uncertain tasks retain their slots until explicit
reconciliation; a waiting queue/coordinator is not enabled for exclusive
destination resources. Runtime failure cannot release all groups non-atomically.
