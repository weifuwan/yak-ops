# DATA_SYNC Task Plugin

DATA_SYNC turns one versioned V4 Task definition into one authorized YakFlow pipeline. It is a
**task plugin**, not a second CDC runtime or a generic workflow/DAG engine.

## Read the implementation in this order

1. `DataSyncTaskPlugin` — platform SPI: configuration validation, publication checks,
   admission, solution listing, and creation of a Task execution handle.
2. `config/JobConfig` — parse V4 definition into ordered table routes and execution intent.
   `config/SourceConfig`, `SinkConfig`, and `TableConfig` project endpoint settings.
3. `solution/SolutionCatalog` — choices displayed in the product; `SolutionValidator`
   owns the corresponding permitted combinations. No physical datasource access here.
4. `validation/JobConfigValidator` — publication-time, workspace-scoped configuration
   and resource checks. `SchemaPlanner` plans DDL; `SchemaValidator` checks safety.
   `SchemaValidationMessages` maps known failures to safe user-facing messages.
5. `plan/JobPlanner` — resolve authorized physical metadata and revisions, validate table
   writes, compute `JobPlan` and immutable `TaskExecutionResources`. No credentials or DDL.
6. `execution/DataSyncTaskExecutor` — re-check admission and return **one deferred**
   `DataSyncTaskHandle`, regardless of the selected solution.
7. `execution/DataSyncTaskHandle` — when `start()` is called by the platform, owns the
   complete preparation, submission, cancellation and completion sequence.
8. `pipeline/PipelineBuilder` — compile verified routes into Source/Sink, YakFlow
   `StreamGraph` and execution configuration. It is not a task scheduler.
9. `execution/TableExecutionProgress` — collect live state and metrics;
   `DataSyncExecutionReport` projects them into the existing public JSON reports.

## Execution sequence

```text
V4 parameters
  -> JobConfig / JobConfigValidator (static and publication checks)
  -> JobPlanner (authorized metadata and TaskExecutionResources)
  -> DataSyncTaskExecutor.start (admission fence, no engine submission)
  -> platform registers TaskHandle and persists RUNNING
  -> DataSyncTaskHandle.start
     -> JobPlanner (re-admission)
     -> IncrementalBatchPreparation (only for bounded offline incremental:
        source-clock upper bound, durable TaskCursorStore.freeze)
     -> SchemaPreparation (only when SCHEMA is requested:
        perform DDL, read back and validate each target)
     -> JobPlanner (re-read all metadata after possible DDL, re-admission)
     -> IncrementalRun.apply (restore frozen source scan range if relevant)
     -> PipelineBuilder.build (physical Source/Sink and runtime configuration)
     -> PipelineExecutor.execute / YakFlowTaskHandle
     -> outcome, cancellation, progress and report
```

A single table and multiple explicitly mapped tables use the same collection-based JobPlan.
The physical graph normally contains one table-aware Source and one table-aware Sink;
runtime data stages are driven by the Source, not by a generic workflow graph.

## Module boundaries

| Owner | Responsible for | Must not do |
| --- | --- | --- |
| `config` | V4 shape, table mappings, connector-specific option projections | Open connections or execute DDL |
| `solution` | Advertised product choices and their static constraints | Inspect physical tables |
| `validation` | Publication-time checks, schema plan and safety | Launch jobs |
| `plan` | Physical metadata, bound table routes and admission fingerprint | Freeze cursor or submit jobs |
| `execution` | Preparation, TaskHandle lifecycle, report observations | Reimplement CDC split/checkpoint algorithms |
| `pipeline` | Source/Sink factory calls, stream graph and engine configuration | Decide Task status or own cancellation |

## Correctness boundaries

- Persisted V4 JSON remains the product contract; `SCHEMA` is a derived preparation
  stage, not a separately persisted user-selected data step.
- Admission ties the job to approved datasource revisions and physical schemas.
  It is checked again immediately before physical execution and after target DDL.
- Bounded offline incremental freezes its cursor window **before** modifying targets.
  Retries reuse the frozen window; they cannot silently advance the upper bound.
- The same Task Instance may restore its own Checkpoint. Another Instance does not
  inherit that Checkpoint state.
- CDC Snapshot/Hybrid/Binlog handling belongs to the MySQL CDC Connector.
  The task plugin consumes its boundedness and StartupOptions; it does not copy CDC
  assignment, backfill or handoff state machines.
- Requesting cancellation is not proof of a terminal CANCELED outcome. Unknown driver
  DDL or engine submission outcomes remain distinguishable from confirmed failures.
- Public progress and reports must not expose credentials, record payloads or raw
  database exceptions. Existing JSON versions and count/recovery semantics stay stable.

## When changing code

Change a rule in its owning layer rather than repeating it in the UI, catalog and
planner. Use existing Flink/SeaTunnel-style names only where they reflect the actual
responsibility. Avoid generic managers, codec wrappers, abstract processor chains and
future-connector scaffolding. Keep regression tests for admission, cursor freezing,
CDC startup modes, schema readback, cancellation and report compatibility.
