# Yak Ops Data Sync Runtime

Owns V5 Pipeline planning and execution independently of the scheduler Task plugin.
`io.yak.ops.sync.runtime.PipelineService` is the single V5 definition,
resource admission and deferred execution submission facade used by the DATA_SYNC plugin.

The runtime contains the internal configuration projection, authenticated datasource
planning, JDBC and CDC connector selection, schema and cursor preparation,
physical PipelineBuilder, metrics and YakFlow PipelineExecution implementation.

These are engine implementation details, not Task parameters. Persistent definitions
are V5 Pipeline JSON; legacy V4 is unsupported. JobClient reports physical runtime status,
cancel completion and authoritative terminal outcomes. The Task platform retains
ownership of TaskGroup admission, Instance state and recovery.
