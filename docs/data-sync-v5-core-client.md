# V5 Pipeline Definition
The persisted DATA_SYNC task definition is V5 only. Legacy V4 is not recognized
or adapted, and V5 is never translated back into V4 for execution.

PipelineDef, SourceDef, SinkDef, RouteDef, TransformDef, PipelineComposer and
PipelineExecution follow Flink CDC 3.7 naming. YakFlow provides the physical runtime;
DolphinScheduler Task only controls the execution lifecycle.

Example:
```json
{"source":{"type":"MYSQL_CDC","datasourceId":"source",
 "startup":{"mode":"INITIAL"},"tables":[{"database":"app","table":"orders"}]},
 "sink":{"type":"JDBC","datasourceId":"target","schemaPolicy":{"initial":"VERIFY",
 "evolution":{"enabled":false,"allowDropColumns":false}},
 "tables":[{"schema":"public","table":"ods_orders","write":{"mode":"UPSERT"}}]},
 "route":[{"source-table":"app.orders","sink-table":"public.ods_orders"}],
 "transform":[],"pipeline":{"execution.runtime-mode":"STREAMING"}}
```

Explicit one-to-one routes remain the currently supported physical execution subset.
Unknown connector combinations and unsupported transformations fail validation.

Composer alignment: `PipelineComposer` is an interface with definition-to-execution semantics;
`YakFlowPipelineComposer` owns the embedded implementation. Parsing remains in the
parser, and Task scheduling owns job registration, status and cancellation.
Task submission revalidates metadata before composing; the deferred execution
revalidates its admitted resources before any physical side effects.
