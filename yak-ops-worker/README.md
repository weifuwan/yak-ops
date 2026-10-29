# Yak Ops Worker

DolphinScheduler `dolphinscheduler-task-executor` and
`dolphinscheduler-worker/executor` are the upstream reference for the
TaskEngine → Container → TaskExecutorWorker → PhysicalTaskExecutor architecture.

The first implementation is embedded: one JVM, one Master, a configurable
number of exclusive task slots, no remote RPC or remote Worker discovery. The Yak Ops Task
SPI and its deferred TaskHandle lifecycle remain authoritative for physical
execution. The platform persists the Task Instance and does not release
Task Group slots on an unconfirmed Job result.

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
  Task Group slot and authorization, a local rather than RPC lifecycle ACK, and no
  remote log upload or worker filesystem staging.
- The runtime context is transportable **data**, not a holder of factories or
  callbacks. Workflow/project fields stay nullable until a Workflow model
  supplies authoritative values. No invented Workflow identifiers.
- A Physical Task does not relinquish ownership on unconfirmed async engine
  completion; the business `RECONCILING` state and dispatch token remain
  authoritative. Run and inspect all backend checks before merging.

## DolphinScheduler JDBC Registry

The embedded Master uses directly migrated DolphinScheduler **3.4.3**
Registry API and JDBC source in `yak-ops-registry-api` /
`yak-ops-registry-jdbc`, without an external DolphinScheduler Registry
Maven dependency. The four `yak_ops_jdbc_registry_*` tables retain their
upstream DDL; compare `yak-ops-registry-jdbc/UPSTREAM.md` for adaptations. The previous `yak_ops_task_execution_owner`
row and its repository are deleted. Registration uses an ephemeral Registry
path and Registry lock. The embedded filesystem lock remains a process-local
safety guard, and new dispatch fails closed when Registry ownership is lost.
Uncertain physical executions are not blindly resumed by a successor Master.

## DolphinScheduler Task Group slot migration

The old `yak_ops_task_execution_claim` table is replaced by `yak_ops_task_group`
and `yak_ops_task_group_queue`. The SQL slot CAS follows DolphinScheduler's
`TaskGroupMapper.xml` `acquireTaskGroupSlot` / `releaseTaskGroupSlot`.
Yak Ops uses a Workspace-scoped Task Group of capacity one per canonical
exclusive resource and permits one Task Instance to reserve multiple groups
in a transaction, sorted by resource identity. Queue `task_id` uses the Yak
Ops string Task Instance ID. Uncertain tasks retain their slots until explicit
reconciliation; a waiting queue/coordinator is not enabled for exclusive
destination resources. Runtime failure cannot release all groups non-atomically.

### Task Group source boundary

The source-migrated `org.apache.dolphinscheduler.common.enums.Flag` and
`TaskGroupQueueStatus` establish upstream queue/slot status codes; acquisition
writes `ACQUIRE_SUCCESS`, `NO` force-start and `YES` in-queue as DolphinScheduler
does. The live adapter is intentionally **not** an upstream `TaskGroupCoordinator`:
it has no Workflow Instance, waiting task state, queue wakeup, priority arbitration,
or distributed Master event protocol. Do not claim full Task Group Coordinator
migration until those dependencies and recovery transitions are available.

Source baseline: DolphinScheduler dev `ee47c371`, Apache 2.0 headers preserved.
