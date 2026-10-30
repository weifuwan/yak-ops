# YOIP-21: Core Data Sync Client and V5 Pipeline Definition

Status: Implementation PR (draft; V4 editor remains default)

## Architecture

Core owns `DataSyncClient`, `PipelineDef`, `SourceDef`, `SinkDef`,
`RouteDef`, `TransformDef`, and the JSON definition parser.
The runtime-specific adapter still resides in the existing Data Sync task artifact,
and delegates to the existing `JobPlanner`, `DataSyncTaskExecutor`,
`PipelineBuilder`, `PipelineExecutor`, and `JobClient`.
This avoids a Core -> YakFlow Runtime dependency cycle and avoids a second execution algorithm.

The implementation follows the boundaries of Apache Flink CDC 3.7
`PipelineDef` / `PipelineComposer`. It reuses Yak Ops's existing
SeaTunnel-style `FactoryUtil`, `OptionRule`, and `ReadonlyConfig`.
It does not copy Flink's StreamExecutionEnvironment or SeaTunnel's graph runtime.

## V5 JSON example

```json
{
  "source": {
    "type": "MYSQL_CDC",
    "datasourceId": "src",
    "startup": {"mode": "INITIAL"},
    "tables": [{"database": "app", "table": "orders"}]
  },
  "sink": {
    "type": "JDBC",
    "datasourceId": "dest",
    "schemaPolicy": {
      "initial": "VERIFY",
      "evolution": {"enabled": false, "allowDropColumns": false}
    },
    "tables": [{
      "schema": "public", "table": "ods_orders",
      "write": {"mode": "UPSERT"}
    }]
  },
  "route": [{"source-table": "app.orders", "sink-table": "public.ods_orders"}],
  "transform": [],
  "pipeline": {"execution.runtime-mode": "STREAMING"}
}
```

V5 uses an explicit one-to-one route for each source/sink table. No inferred
fan-out, fan-in, transforms, runtime credential injection, or arbitrary
checkpoint overrides are supported. Unimplemented constructs are rejected.

## Core client

- `validate(json, context)`: parse, validate the existing Connector / V4 rules
- `plan(json, context)`: authorize resources and return stable resource claims
- `submit(json, context)`: create and immediately start a logical Data Sync job
- `prepare(json, context)`: create a *deferred* handle, for the Task Worker only
- `getJob(jobId, context)` and `cancel(jobId, context)`: process-local, Workspace-scoped operations

Identity is supplied separately by trusted application code; JSON must not
contain credentials, Workspace identity, execution identity, or state paths.
`DataSyncJob` covers pre-submission preparation as well as physical YakFlow execution.

## Compatibility and limits

Historical V4 Task definitions continue through the original strict V4 parser
and execute through the original preparation and YakFlow engine.
The V4 web editor remains the default creator until a coordinated V5
editor rollout; explicitly pinned schemaVersion=5 is supported by the plugin.
The Core client detects a V4 document by `solutionType` and delegates to its
existing V4 execution implementation.

The process-local job index is **not** a remote execution registry or durable
recovery mechanism. Worker restart/reconciliation remains owned by the existing
Task execution subsystem. The Core client does not bypass Task admission or
durable Instance registration when invoked through the Task plugin.
