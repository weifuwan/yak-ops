# DATA_SYNC Pipeline definitions

The Task API persists immutable JSON definitions with parameter schema version **7**. Source and sink types are installed Connector factory identifiers. The Task plugin converts JSON to YAML before validation and composition; the engine accepts Pipeline definitions independently of the UI transport.

```json
{
  "source": {
    "type": "mysql",
    "datasourceId": "source",
    "startup": {"mode": "INITIAL"},
    "tables": [{"database": "app", "table": "orders"}]
  },
  "sink": {
    "type": "jdbc-postgres",
    "datasourceId": "target",
    "schemaPolicy": {
      "initial": "VERIFY",
      "evolution": {"enabled": false, "allowDropColumns": false}
    },
    "tables": [
      {"schema": "public", "table": "ods_orders", "write": {"mode": "UPSERT"}}
    ]
  },
  "route": [
    {"source-table": "app.orders", "sink-table": "public.ods_orders"}
  ],
  "transform": [],
  "pipeline": {
    "execution.runtime-mode": "STREAMING",
    "pipeline.table-scope": "SINGLE"
  }
}
```

For bounded JDBC execution, select the matching factory such as `jdbc-mysql`, specify `syncMode: "FULL"`, and use `execution.runtime-mode: "BATCH"`. MySQL CDC `startup.mode: "SNAPSHOT"` also uses BATCH. Continuous MySQL startup modes use STREAMING; omitting `startup` in a STREAMING MySQL definition uses `INITIAL`. The selected factory validates the effective mode and options.

Transforms are expressed using Flink CDC fields such as `source-table`, `projection`, `filter`, and `primary-keys`. The Task boundary preserves these rules. The embedded engine supports explicit source tables and one-to-one routes; unsupported operators and options fail validation. See [the engine execution boundary](../yak-ops-engine/README.md#supported-execution-boundary) for the supported subset.

The JSON `pipeline` object also accepts the supported upstream options `name`, `parallelism`, `local-time-zone`, `schema.change.behavior`, `schema.operator.uid`, `route-mode`, and `transform.decimal.precision.mode`. The editor retains these options when saving other fields and synchronizes an explicit `schema.change.behavior` with the evolution switch. `schema.change.behavior` supports `evolve` and `exception` and must agree with the sink evolution policy. Options without an embedded runtime implementation, including `schema-operator.rpc-timeout`, are rejected.

Within each JSON endpoint, tables must consistently include or omit the `database` field. Mixing database-qualified and schema-only table paths is rejected before routing so a two-part name cannot silently change namespace meaning.

`datasourceId` references an authorized Workspace datasource; connection credentials remain server-side. Client-supplied `index` values are ignored. The plugin derives query indexes for source/sink identifiers, runtime mode, table scope, and datasource IDs from the validated Pipeline.

Task submission checks authorized resources and metadata revisions. Composition produces a deferred execution, and physical start follows durable Instance registration. Cancellation, terminal outcome, physical job identity, and progress flow through the Task handle. Editing a definition creates a new immutable version; it does not reinterpret an existing execution's checkpoints.

Bounded JDBC cursor bindings include the effective transform and its expression settings. Changing projection, filter, transformed keys, time zone, or decimal precision behavior requires explicit initialization under a new definition; an existing committed cursor is not silently reused for changed semantics. Cosmetic task changes retain the cursor. JDBC changelog writes assign each target table to one writer, preserving the order of output-key changes and key reuse while allowing different tables to run in parallel.

Earlier parameter schema versions are explicitly rejected and must be recreated using the current contract. No legacy execution adapter or automatic checkpoint conversion is provided.
