# DATA_SYNC Pipeline definitions

## Create a synchronization task

The Data Sync application has two products. Their availability depends on the selected source and target channel.

| Product | Configuration | Execution |
| --- | --- | --- |
| Single-table offline (`单表离线`) | One source table, target fields, projection expressions, row filtering, and JDBC read strategy | A bounded batch, started manually or by a supported schedule |
| Database realtime (`整库实时`) | One or more explicitly selected source tables, one-to-one target mappings, and a CDC read strategy | Continuous replication or a full-only run that finishes |

Select the source and target datasource types, product, and task name in **New synchronization task**. In the editor, select authorized connections and tables, configure the read strategy and target writes, and save the definition. Saving does not publish or start the task. Publish it before starting an execution; configure scheduling separately when the selected strategy supports it.

Single-table offline supports full reads and, where the JDBC channel allows them, field-based increments. **Full then incremental** means the first successful execution initializes the target and subsequent executions read bounded field ranges. Each execution finishes. Incremental reads need an appropriate timestamp or increasing-value column; they do not capture physical deletes or updates that leave the selected field unchanged. Full reads do not imply truncating or overwriting the target.

Database realtime offers these strategies:

| Strategy | MySQL CDC startup | Runtime mode |
| --- | --- | --- |
| Full load followed by continuous changes | `INITIAL` | `STREAMING` |
| Changes only | `LATEST_OFFSET` by default; other supported positions require explicit configuration | `STREAMING` |
| Full load only | `SNAPSHOT` | `BATCH` |

Changes only does not copy existing rows. Full load only remains a database-realtime product in lists and details, but finishes as bounded work and can use a supported batch schedule. Continuous CDC does not use a periodic-launch schedule. Target-structure preparation and ongoing schema evolution are separate settings; continuous evolution is unavailable for full-only execution.

The replication selection contains between 1 and 100 tables and is fixed for the execution. Selecting all current tables does not automatically subscribe to tables created later. Single-table replication and CDC snapshots are strategies within this product, not additional creation choices. Multi-table JDBC is not a Data Sync application product; the underlying Pipeline execution boundary remains separate from the two product workflows.

## Definition format

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
    "pipeline.table-scope": "DATABASE"
  }
}
```

For bounded JDBC execution, select the matching factory such as `jdbc-mysql`, specify `syncMode: "FULL"`, and use `execution.runtime-mode: "BATCH"`. MySQL CDC `startup.mode: "SNAPSHOT"` also uses BATCH. Continuous MySQL startup modes use STREAMING; omitting `startup` in a STREAMING MySQL definition uses `INITIAL`. The selected factory validates the effective mode and options.

Standard replication definitions use `pipeline.table-scope: "DATABASE"`, including a selection of one table. The supported single-table `RETRACT` write path requires `SINGLE` scope and `CHECKPOINT_XA`; that execution constraint does not create another product. The effective scope defaults to `SINGLE` for one source table and `DATABASE` for multiple source tables only when it is omitted.

### Fields and transforms

Field preparation uses the existing `transform` array and Flink CDC fields `source-table`, `projection`, `filter`, and `primary-keys`. Field selection and order are projection expressions; a renamed target field is an output alias. There is no separate persisted field-mapping protocol. Renaming a complete physical key can require explicit transform `primary-keys`; an expression does not establish uniqueness by itself.

Use the field editor for direct mappings and the expression/rule view for rules that cannot be represented exactly as a field table. Wildcards, expression text, selectors, and rule order are retained when unrelated settings change. Publication and admission perform the existing output-schema and target-write validation; saving a draft is not an execution preview. The embedded engine supports explicit source tables and one-to-one routes; unsupported operators and options fail validation. See [the engine execution boundary](../yak-ops-engine/README.md#supported-execution-boundary) for the supported subset.

The JSON `pipeline` object also accepts the supported upstream options `name`, `parallelism`, `local-time-zone`, `schema.change.behavior`, `schema.operator.uid`, `route-mode`, and `transform.decimal.precision.mode`. The editor retains these options when saving other fields and synchronizes an explicit `schema.change.behavior` with the evolution switch. `schema.change.behavior` supports `evolve` and `exception` and must agree with the sink evolution policy. Options without an embedded runtime implementation, including `schema-operator.rpc-timeout`, are rejected.

Within each JSON endpoint, tables must consistently include or omit the `database` field. Mixing database-qualified and schema-only table paths is rejected before routing so a two-part name cannot silently change namespace meaning.

`datasourceId` references an authorized Workspace datasource; connection credentials remain server-side. Client-supplied `index` values are ignored. The plugin derives query indexes for source/sink identifiers, runtime mode, table scope, and datasource IDs from the validated Pipeline.

## Product filtering

`POST /api/v1/task-definitions/page` accepts `parameterFilters.dataSyncProfile` when `taskType` is `DATA_SYNC`. The only values are `SINGLE_TABLE_BATCH` and `DATABASE_REPLICATION`; omitting the filter leaves the query unfiltered by product.

The product is derived from the validated definition. A source with `startup`, or a Pipeline with `execution.runtime-mode: "STREAMING"`, is replication. A remaining definition belongs to single-table offline only when its effective table scope is `SINGLE`. These labels are not new fields in Pipeline parameters, and connector name prefixes do not determine the product.

For example, this query selects bounded replication tasks, including CDC full-only snapshots:

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "taskType": "DATA_SYNC",
  "parameterFilters": {
    "dataSyncProfile": "DATABASE_REPLICATION",
    "runtimeMode": "BATCH"
  }
}
```

The exact filters `sourceType`, `sinkType`, `runtimeMode`, `tableScope`, `sourceDataSourceId`, and `targetDataSourceId` combine with the product filter using `AND`. `sourceType` and `sinkType` refer to factory identifiers such as `mysql` and `jdbc-mysql`, not datasource labels such as `MYSQL`. Filtering and total counts are calculated before pagination within the current Workspace. Runtime-based operations statistics retain their `BATCH`/`STREAMING` meaning.

## Versions and execution

Task submission checks authorized resources and metadata revisions. Composition produces a deferred execution, and physical start follows durable Instance registration. Cancellation, terminal outcome, physical job identity, and progress flow through the Task handle. Editing executable parameters creates a new immutable version; changing only the task name or description does not. Execution history uses the definition version bound to each Instance and does not reinterpret existing checkpoints or executions using the latest task configuration.

Bounded JDBC cursor bindings include the effective transform and its expression settings. Changing projection, filter, transformed keys, time zone, or decimal precision behavior requires explicit initialization under a new definition; an existing committed cursor is not silently reused for changed semantics. Cosmetic task changes retain the cursor. JDBC changelog writes assign each target table to one writer, preserving the order of output-key changes and key reuse while allowing different tables to run in parallel.

Earlier parameter schema versions are explicitly rejected and must be recreated using the current contract. No legacy execution adapter or automatic checkpoint conversion is provided.
