# DATA_SYNC Task Plugin

This module is intentionally only a DolphinScheduler-style adapter:

- `PipelineTaskChannelFactory` registers the stable `DATA_SYNC` type.
- `PipelineTaskChannel` parses the parameter envelope and delegates validation and admission to `PipelineService`.
- `PipelineParameters` carries the persisted V5 Pipeline JSON (schema version 5).
- `PipelineTask` adapts the deferred PipelineExecution lifecycle to the TaskHandle contract.

No engine planning, schema changes, CDC handling, cursor planning, connector configuration,
or running-job registry belongs in this plugin. Those implementations live in
`yak-ops-data-sync-runtime`. The V5 PipelineDef and PipelineComposer APIs stay in
`yak-ops-core`; actual Job submission is performed by YakFlow via PipelineExecutor.

A Task instance must be durable before invoking PipelineExecution.start(). Cancellation,
resource admission and uncertain submission outcomes retain their existing semantics.
