# DATA_SYNC Pipeline definitions

## Create a synchronization task

The Data Sync application has two products. Their availability depends on the selected source and target channel.

| Product | Configuration | Execution |
| --- | --- | --- |
| Single-table offline (`单表离线`) | One source table, target fields, projection expressions, row filtering, and JDBC read strategy | A bounded batch, started manually or by a supported schedule |
| Database realtime (`整库实时`) | Explicit or supported regular-expression table selection, one-to-one target mappings, and a CDC read strategy | Continuous replication or a full-only run that finishes |

Select the source and target datasource types, product, and task name in **New synchronization task**. In the editor, select authorized connections and tables, configure the read strategy and target writes, and save the definition. Saving does not publish or start the task. Publish it before starting an execution; configure scheduling separately when the selected strategy supports it.

Single-table offline supports full reads and, where the JDBC channel allows them, field-based increments. **Full then incremental** means the first successful execution initializes the target and subsequent executions read bounded field ranges. Each execution finishes. Incremental reads need an appropriate timestamp or increasing-value column; they do not capture physical deletes or updates that leave the selected field unchanged. Full reads do not imply truncating or overwriting the target.

Database realtime offers these strategies:

| Strategy | MySQL CDC startup | Runtime mode |
| --- | --- | --- |
| Full load followed by continuous changes | `INITIAL` | `STREAMING` |
| Changes only | `LATEST_OFFSET` by default; other supported positions require explicit configuration | `STREAMING` |
| Full load only | `SNAPSHOT` | `BATCH` |

Changes only does not copy existing rows. Full load only remains a database-realtime product in lists and details, but finishes as bounded work and can use a supported batch schedule. Continuous CDC does not use a periodic-launch schedule. Target-structure preparation and ongoing schema evolution are separate settings; continuous evolution is unavailable for full-only execution.

The replication selection contains between 1 and 100 tables. Explicit selection is fixed for the execution; selecting all current tables does not subscribe to future tables. The separate regular-expression mode can discover new tables when its continuous CDC and schema-evolution prerequisites are satisfied. The same 100-table bound includes discovered tables. Single-table replication and CDC snapshots are strategies within this product, not additional creation choices. Multi-table JDBC is not a Data Sync application product; the underlying Pipeline execution boundary remains separate from the two product workflows.

### Editor bodies

The two products share the header, drawers, fixed footer, and save/publication lifecycle. Their bodies follow different workflows:

| Product | Sections, in page and anchor order |
| --- | --- |
| Single-table offline | Datasources; Source data (table, database-side filter, split key, preview); Destination (table, ordered preparation/completion SQL, key-conflict policy); Destination field mappings |
| Database realtime | Datasources; Source data (explicit two-pane table transfer or regular-expression rules); Target table mappings |

The realtime transfer separates the available and selected tables, with database/table filters, refresh, pasted table lists, selection counts, and explicit add/remove actions. Filtering or paging the available catalog does not clear already selected tables. Switching selection mode requires confirmation before discarding the other mode's selection. Catalog pagination is independent of the task's 100-table limit.

Realtime mappings use a horizontally scrollable table. Each row exposes the source and destination names, naming customization, field assignments, target keys, snapshot split column, DML selection, snapshot skip/filter, advanced transform rules, and supported connector parameters. The selection checkbox, operation column, status filter, and “only tables added in this edit” filter operate on stable source-table identities. Batch changes apply only to explicitly selected rows. A locally complete row is not evidence that publication or physical execution validation has passed.

Resource-group, intelligent-processing, and IDE navigation areas are outside this editor. Common settings stay in the existing drawers; a per-table field edits only that table. Expressions and assignments continue to use `transform`, including rules that cannot be represented as direct field pairs.

## Edit, save, and publish

The editor header shows **Task list / task name** (`任务列表 / 任务名称`). The main area contains datasource selection, source tables, and target table or field mappings. The header opens three right-side drawers:

| Drawer | Contents |
| --- | --- |
| Properties (`属性`) | Task name, synchronization solution, task ID, and description |
| Advanced configuration (`高级配置`) | Read strategy, write settings, runtime parameters and diagnostics, supported scheduling, DDL policy, and supported engine options |
| Versions (`版本`) | Saved parameter versions, parameter details and comparisons, restoration, and successful publication history |

Per-table write modes and keys remain with target table mappings. Advanced configuration exposes the capabilities supported by the selected connectors and execution strategy; the DDL and engine controls do not add support for arbitrary engine options. Confirming Properties or Advanced configuration applies its values to the page. Canceling or closing that drawer discards its unapplied edits. Use the fixed **Save** (`保存`) and **Publish** (`发布`) actions at the bottom to persist the page. There is no conversion-to-script action.

**Save** updates the task's latest saved configuration. Tasks can be saved while published or while an instance is active. A parameter change appends an immutable parameter version; changing only the name, description, or Debug setting does not append a parameter version. These saves leave the currently published parameter version and diagnostics setting unchanged.

**Publish** first saves pending page changes, then validates and publishes a saved parameter version together with the saved Debug setting. Publication selects the configuration for subsequent execution submissions. It does not start an instance, restart an active instance, or enable a schedule. Existing admitted and queued instances retain their captured parameter version and Debug setting. The footer distinguishes unsaved edits, the latest saved version, the currently published version, and saved changes awaiting publication. Changes to Debug can require publication even when the parameter version number is unchanged.

Periodic scheduling has its own configuration and revision. Saving task changes and a changed schedule uses separate requests, so they are not one atomic operation. If the task save succeeds but the schedule request fails or its result is unknown, the editor reports that the task was saved, retains the schedule input, and stops before publication. Reload the schedule state before deciding how to continue. A saved schedule is not automatically enabled. Stop an enabled schedule before changing its timing rule; publishing a version that cannot use periodic scheduling is also rejected while that schedule remains enabled. Unpublishing requires no active instance, disables the task's schedule, and clears the published configuration while retaining saved parameter versions.

Task writes use a task revision to detect concurrent edits; schedule writes use the separate schedule revision. On a conflict, the editor retains local input so the user can inspect remote state before replacing it. A publish request uses an idempotency key: retrying that same request does not create a second publication. A successful task save remains saved if later publication validation fails, and the previous published configuration remains available for execution.

## Definition format

The Task API writes immutable JSON definitions with parameter schema version **8**. Published schema **7** definitions remain readable through an explicit reader with their original defaults; history is never rewritten on view, compare, or restore. Saving an edited/restored definition uses schema 8 and creates a new saved version if either the canonical payload or its schema version changes. Source and sink types are installed Connector factory identifiers. The Task plugin converts JSON to YAML before validation and composition; the engine accepts Pipeline definitions independently of the UI transport.

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

Use the field editor for direct mappings and the expression/rule view for rules that cannot be represented exactly as a field table. Wildcards, expression text, selectors, and rule order are retained when unrelated settings change. Publication and admission perform the existing output-schema and target-write validation; saving a draft is not an execution preview. The embedded engine supports one-to-one routes; unsupported operators and options fail validation. See [the engine execution boundary](../yak-ops-engine/README.md#supported-execution-boundary) for the supported subset.

The JSON `pipeline` object also accepts the supported upstream options `name`, `parallelism`, `local-time-zone`, `schema.change.behavior`, `schema.operator.uid`, `route-mode`, and `transform.decimal.precision.mode`. The editor retains these options when saving other fields and synchronizes an explicit `schema.change.behavior` with the evolution switch. `schema.change.behavior` supports `evolve` and `exception` and must agree with the sink evolution policy. Options without an embedded runtime implementation, including `schema-operator.rpc-timeout`, are rejected.

Within each JSON endpoint, tables must consistently include or omit the `database` field. Mixing database-qualified and schema-only table paths is rejected before routing so a two-part name cannot silently change namespace meaning.

`datasourceId` references an authorized Workspace datasource; connection credentials remain server-side. Client-supplied `index` values are ignored. The plugin derives query indexes for source/sink identifiers, runtime mode, table scope, and datasource IDs from the validated Pipeline.

### Table-level options

| JSON location | Meaning and limits |
| --- | --- |
| `source.tables[].read.where` | JDBC source predicate without the `WHERE` keyword; combined with split/cursor bounds using parenthesized `AND` |
| `source.tables[].read.splitKey` | Explicit JDBC split column, checked against the actual physical schema and supported unique-key requirements |
| `source.tables[].options.fetchSize` | JDBC read fetch size, 1–10000; default 500 |
| `source.tables[].options.queryTimeoutSeconds` | JDBC query timeout, 1–300 seconds; default 60 |
| `source.tables[].snapshot.chunkKeyColumn` | MySQL CDC full-load split column |
| `source.tables[].snapshot.skip` | Skip this table's historical rows while retaining CDC snapshot-watermark coordination |
| `source.tables[].snapshot.where` | CDC full-load predicate; it does not filter the subsequent change stream and cannot be combined with `skip: true` |
| `source.tables[].dml.events` | Nonempty unique subset of `INSERT`, `UPDATE`, `DELETE`; omitted means all original Binlog mutations |
| `sink.tables[].write.preSql`, `postSql` | Ordered bounded-JDBC preparation/completion statement arrays, at most 32 statements in each array |
| `sink.tables[].write.batchSize` | Per-target JDBC flush threshold, 1–10000; default 500; unavailable for checkpoint XA/RETRACT |
| `sink.typeMappings` | Explicit supported source-family to output-type rules, also applied to target-schema validation and preparation |

Source predicates are database-side reads, distinct from `transform.filter`. A split key does not become an incremental cursor or a target key. Predicates, split identity, fetch settings, and physical schema information are frozen into JDBC split checkpoints; recovery rejects changed semantics instead of resuming a different query. Binary, high-precision numeric, and temporal preview values are rendered without JavaScript numeric rounding.

DML selection occurs before projection, filtering, and write-mode conversion. Both images of an UPDATE are accepted or skipped together, and skipping an event still advances the source position. DML selection does not suppress full-load snapshot rows. Selecting fewer events can intentionally leave the target different from the source; it is not a dirty-row tolerance setting.

MySQL JDBC full reads additionally support native `REPLACE` for key conflicts. It uses MySQL replacement semantics and can delete and insert a conflicting row; it is not an alias for UPSERT. Continuous CDC and cursor-based incremental JDBC retain their existing UPSERT/RETRACT constraints, and other target databases do not expose MySQL REPLACE.

SQL hooks accept one validated `INSERT`, `UPDATE`, `DELETE`, or `TRUNCATE` per array entry, restricted to the configured target table. Arbitrary DDL, session/transaction control, multiple statements per entry, cross-table operations, and unrecognized expressions are rejected. Hooks require bounded, non-XA JDBC execution and a durable execution state directory. Preparation finishes before readers start; completion runs after data is durably complete. A persisted completion marker prevents replaying preparation or reimporting data while retrying completion. A known rolled-back statement can be retried; an unresolved started statement fails closed on recovery. This is not a promise of exactly-once execution for arbitrary SQL. Starting a new task instance starts a new hook lifecycle.

Type mappings expose a bounded set of widening conversions. Decimal and character rules validate actual precision/length for every affected column; explicit field CAST expressions take precedence. A rule participates in the transform identity, output schema, and physical target checks. The editor obtains supported rules and parameter keys from `/api/v1/data-sync/connectors`; an editable JSON field does not imply arbitrary engine-option support.

### Dynamic regular-expression selection

Regular-expression selection uses `source.selection` with `mode: "REGEX"`, `databasePattern`, and `tablePattern`, together with `sink.mapping`. It requires MySQL CDC `INITIAL` startup, STREAMING execution, DATABASE table scope, `schemaPolicy.initial: "CREATE_OR_ALTER"`, and enabled schema evolution. Target defaults use UPSERT without XA, hooks, or custom target-key overrides; each table supplies its own physical primary key. Static selection omits `selection` and `mapping` entirely.

```json
{
  "source": {
    "selection": {
      "mode": "REGEX",
      "databasePattern": "app",
      "tablePattern": "orders.*",
      "snapshot": {"skip": false},
      "dml": {"events": ["INSERT", "UPDATE", "DELETE"]}
    }
  },
  "sink": {
    "mapping": {
      "database": "ods",
      "table": "${table}",
      "write": {"mode": "UPSERT", "batchSize": 500}
    }
  }
}
```

This fragment adds selection and mapping rules to the full definition's required endpoint and pipeline fields. Target templates accept only literal identifier fragments and `${database}`, `${schema}`, `${table}`. The saved `tables` and `route` entries are the initial resolved selection and its explicit per-table overrides. Changing the new-table defaults does not silently rewrite those overrides; a batch edit is an explicit way to change existing rows.

Runtime discovery uses the published rules, validates new source/target schemas, prepares the target through the schema coordinator, and then joins the existing CDC snapshot and incremental protocol. New tables use the declared defaults, never the first table's overrides. Discovered tables and their schema state belong to the instance checkpoint; discovery does not modify the task definition or append parameter versions. Recovery restores this state before accepting further rows. Duplicate target mappings, the 100-table limit, and unsupported new schemas stop admission instead of silently dropping tables.

A dynamic task reserves its target datasource for its execution; it cannot overlap another admitted table writer using that datasource in the same Workspace. Static tasks targeting different tables can still run concurrently. For MySQL-to-MySQL dynamic mappings, actual server UUIDs are checked so a target matching the source selection cannot create a feedback loop through a different connection alias. Identity-query failures stop validation.

### Source preview and catalog APIs

`POST /api/v1/data-source/{id}/catalog/table-page` accepts `database`, `schema`, `keyword`, `databasePattern`, `tablePattern`, `offset`, and `limit` (at most 500). It returns `{items, nextOffset}`; `nextOffset: null` means the catalog page sequence has finished. Filters are applied before page slicing, so matching tables beyond the first 500 metadata entries remain reachable. These requests use saved, authorized datasource credentials within the current Workspace. Pagination and preview resolve omitted database/schema values from the datasource binding and reject namespaces outside that binding; the connection is pinned to the authorized datasource revision.

`POST /api/v1/data-source/{id}/catalog/preview` accepts `{database?, schema?, table, where?, limit?}` and returns `{columns, rows, truncated}`. The default is 20 rows and the maximum is 100, with bounded cells, total response size, and query timeout. It uses the current unsaved source table and predicate only. Preview neither executes sink SQL nor saves, publishes, or starts a task. BIGINT/DECIMAL values remain strings; failure is reported as a preview failure rather than an empty successful dataset.

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

The Versions drawer separates **Development history** (`开发记录`) from **Publication history** (`发布记录`). Development history lists saved parameter versions with their author and creation time. Open a version to view its parameters or compare two saved versions. **Restore to current edit** (`还原到当前编辑`) replaces the page's parameter edits after confirmation; it preserves the current name, description, Debug setting, and schedule. Restoration does not modify a historical version or change the published task. Save the restored parameters to append a new version when they differ from the latest saved parameters, then publish when ready. Unsupported historical parameter schemas are not silently converted into the current schema.

Publication history records successful publications with parameter version, the captured Debug setting, publisher, time, and request ID. It is independent of parameter version numbering: the same parameter version can be published more than once, including with a different Debug setting. A repeated request with the same idempotency key and intent returns the current task state without publishing again. Reusing that key with a different version or source revision is rejected. Failed publication attempts are not success-history entries.

The Task API distinguishes the following fields on a task definition:

| Field | Meaning |
| --- | --- |
| `definitionVersion` | Latest saved immutable parameter version |
| `debug` | Latest saved diagnostics setting |
| `revision` | Task edit/publication revision used for concurrent-write checks |
| `publishedVersion`, `publishedDebug` | Published configuration for subsequent submissions; both are null while unpublished |
| `hasUnpublishedChanges` | Whether saved parameters or diagnostics differ from the published configuration |

`PUT /api/v1/task-definitions/{id}` requires `expectedRevision`. `POST /api/v1/task-definitions/{id}/publish` requires `{ "version": 3, "expectedRevision": 8, "requestId": "client-generated-unique-key" }` and returns the current task definition. The selected version must already be saved. Publication freezes the saved Debug setting at the supplied task revision; Debug is not part of the immutable parameter JSON. Name and description remain task metadata and are not restored by selecting a parameter version.

`GET /api/v1/task-definitions/{id}/versions?pageNo=1&pageSize=20` returns a paginated summary without parameter payloads. Fetch the full parameter-version record with `GET /api/v1/task-definitions/{id}/versions/{version}`. `GET /api/v1/task-definitions/{id}/publications?pageNo=1&pageSize=20` returns paginated publication history. Both lists use `bizData` and `pagination`, accept page sizes from 1 to 200, and are restricted to the current authorized Workspace. Parameter schema version, parameter version, task revision, and schedule revision are separate values.

Task submission uses the published parameter version and diagnostics setting and checks authorized resources and metadata revisions. Composition produces a deferred execution, and physical start follows durable Instance registration. Cancellation, terminal outcome, physical job identity, and progress flow through the Task handle. Execution history uses the definition version bound to each Instance and does not reinterpret existing checkpoints or executions using the latest saved or published task configuration. Saving a new version or changing Debug does not alter subsequent submissions until that configuration is published.

Bounded JDBC cursor bindings include the effective transform and its expression settings. Changing projection, filter, transformed keys, time zone, or decimal precision behavior requires explicit initialization under a new definition; an existing committed cursor is not silently reused for changed semantics. Cosmetic task changes retain the cursor. JDBC changelog writes assign each target table to one writer, preserving the order of output-key changes and key reuse while allowing different tables to run in parallel.

Parameter schema 7 has the explicit read path described above; versions other than 7 and 8 are rejected. The reader does not add newly supported options to an old version. Existing checkpoint formats remain explicitly versioned; no unversioned or heuristic conversion is provided.

## Upgrade existing tasks

The forward Flyway migration `V2__v1_4_0.sql` adds the task revision, the published parameter/diagnostics reference, and successful publication history. Existing tasks start at revision 1. For an already published task, the migration sets `publishedVersion` to its existing current parameter version and `publishedDebug` to its existing Debug setting. Unpublished tasks keep both published fields null. Existing immutable parameter versions, instance bindings, and checkpoints are unchanged. History records begin with successful publications made after the upgrade; the migration does not fabricate earlier publication events.

Upgrade backend processes together with the updated frontend/API client. Stop all old backend processes before applying V2 and starting the new backend; do not run old and new backend versions against the same database. Old backends select the latest saved version for execution and do not implement the published-reference or revision contract. This migration does not provide a mixed-version deployment or an automatic database downgrade. API clients must supply the required task revision and publication request body and handle the paginated version-summary response.
