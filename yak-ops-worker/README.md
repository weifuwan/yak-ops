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
