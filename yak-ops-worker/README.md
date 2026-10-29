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
