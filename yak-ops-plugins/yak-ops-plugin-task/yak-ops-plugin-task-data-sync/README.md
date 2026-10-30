# Pipeline Task Plugin (DATA_SYNC)

The one persisted Task definition is V5. It follows Flink CDC 3.7's
`PipelineDef` / `PipelineComposer` boundaries and uses existing YakFlow
source, sink, checkpoint and schema-planning algorithms. No V4 parser,
V4-to-V5 migration or V5-to-V4 adapter is available.

## Code ownership

- `io.yak.ops.core.definition`: `PipelineDef`, `SourceDef`, `SinkDef`,
  `RouteDef`, `TransformDef`. Only one complete V5 definition is accepted.
- `io.yak.ops.core.parser`: strict JSON-to-`PipelineDef` parsing.
- `io.yak.ops.core.composer`: `PipelineComposer`, `PipelineExecution`,
  `PipelinePlan` and `PipelineContext`; the implementation is `YakFlowPipelineComposer`.
  This interface owns no running-job registry and performs no cancellation or submission.
- `PipelineTaskPlugin`: DolphinScheduler-style Task SPI, registration,
  validation, admission and a thin deferred execution handle.
- `execution/YakFlowPipelineComposer`: binds the V5 composer to the verified
  `JobPlanner`, `PipelineBuilder` and YakFlow executor.
- `config/JobConfig`: internal typed per-table execution projection of V5,
  **not a persisted V4 wire format**. Source/Sink/Route are read from V5.
- `solution/SolutionCatalog`: advertised user-facing choices such as
  single-table/batch and database/CDC. Product `solutionType` is derived and
  must not be persisted in a V5 pipeline.
- `plan/JobPlanner`: authorized datasource metadata, table identities and
  resource admission; preserves existing Schema and CDC checks.
- `execution/PipelineTaskExecutor` and `PipelineTaskHandle`: physical
  preparation and lifecycle behind the composer.
- `pipeline/PipelineBuilder`: table-aware Source and Sink factories, stream
  graph and runtime settings.

## Execution

```text
V5 JSON -> JsonPipelineDefinitionParser -> PipelineDef
        -> PipelineComposer.plan
        -> JobPlanner (authorized metadata and admission)
        -> PipelineTaskPlugin registers deferred PipelineExecutionTaskHandle
        -> PipelineExecution.start() (after Task Instance registration)
        -> PipelineTaskHandle (schema/cursor preparation, re-admission)
        -> PipelineBuilder -> YakFlow PipelineExecutor
        -> completion / status / cancellation / report
```

Source and sink credentials, workspace identity, execution IDs, and checkpoint
paths come from trusted application context and datasource lookup, never from
user-supplied pipeline JSON. `getJob` and `cancel` are currently restricted
to the owning process. Cross-worker recovery remains the Task platform's
responsibility.

## Current execution capabilities

JDBC batch and incremental sources, MySQL CDC sources and JDBC sinks,
with 1:1 explicit physical table routes. Unsupported Flink CDC routing
patterns and transforms are rejected rather than silently ignored.
The V5 schema version is 5; V4 definitions are deliberately no longer
readable or executable. Do not silently migrate stored V4 data.

Preserve existing CDC snapshot, binlog handoff, checkpoint restoration,
schema evolution, XA and cancellation semantics when changing this code.
