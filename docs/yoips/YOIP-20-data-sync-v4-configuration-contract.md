# YOIP-20: Data Sync V4 Component-Owned Configuration Contract

Status: Discussion

Discussion: https://github.com/weifuwan/yak-ops/pull/1744

Decision: Pending maintainer review

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A; SeaTunnel's configuration/OptionRule/Factory model is a design reference, not a claim of API compatibility.

## Motivation

Data Sync task parameters have grown across multiple independently edited locations. The current version-three contract stores datasource identities in top-level `source` and `sink`, table identities in `tableMappings`, write mode alongside the mapping, snapshot keys in `tableMappings.source`, transaction intent as `source.xa`, and schema controls at job root. The editor additionally flattens Source, Sink, and job fields into one `EditorForm`, then copies them through a save payload and another serializer. A single new connector option therefore requires edits across product UI, TypeScript validation, plugin parsing, and planner configuration.

`readSyncParameters` currently combines saved-definition interpretation with execution-oriented constraints. A task page can consequently fail when one stored definition no longer satisfies the latest execution rules. The frontend list-isolation fix is necessary but not a substitute for distinct read, validate, plan, and execute contracts.

YakFlow already has `Option`, `OptionRule`, `ReadonlyConfig`, Connector Factories, `JobPlanner`, `PipelineBuilder`, and runtime `ExecutionOptions`. This proposal reuses those boundaries. The reference is Apache SeaTunnel's component-owned Source/Transform/Sink configuration and job-level Env, not its HOCON syntax or an unrestricted graph DSL.

[YOIP-5](YOIP-5-data-integration-sync-product-model.md) remains the accepted V3 product design. This proposal would **supersede only the persisted V3 parameter shape and the redundant schema-step representation for newly created V4 definitions after acceptance and implementation**. Solution identity, Workspace security, immutable Definition Versions, scheduling ownership, and existing runtime reliability constraints remain intact.

## Public Interfaces

### Versioned task definition

After implementation, new `DATA_SYNC` definitions use `parameterSchemaVersion=4`, recorded by the server with the immutable Definition Version. The client must not choose the schema-version field. Definition identity, task type, name, description, debug flag, Workspace identity, publish status, scheduling, and Instance identity remain properties of the Task platform, **not** of the job parameter JSON.

The V4 job document has the following top-level properties. Unknown properties are rejected by a strict V4 parser rather than ignored or implicitly mapped.

| Property | Contract and owner |
| --- | --- |
| `solutionType` | Required existing product solution identifier; single-table/database-wide and offline/realtime capabilities are resolved from this value and actual datasource types. |
| `steps` | Required ordered **data** steps, `FULL` and/or `INCREMENTAL`; real-time combinations remain limited to those supported by the registered solution. No `SCHEMA` step is persisted in V4. |
| `source` | Required Source connection reference, selected source tables, and table-specific read/snapshot/incremental options. |
| `transform` | Required empty array in the first V4 release. Nonempty transforms are rejected, not silently skipped. |
| `sink` | Required Sink connection reference, target tables linked to Source outputs, write identity/transaction options, and target schema behavior. |
| `env` | Required object, initially empty for user-authored V4 definitions. Physical Runtime options are system-owned until explicitly exposed by a reviewed product capability. |

Connector factory identifiers such as `JDBC` and `MYSQL_CDC`, physical JDBC connection credentials, generated CDC replication IDs, XA instance IDs, checkpoint directories, and server-managed state must not be present in submitted V4 parameters. Datasources are referenced by Workspace-scoped IDs and resolved by the backend.

### Single-table realtime example

This is a **proposed V4 fixture**, not accepted by the current V3 endpoint:

```json
{
  "solutionType": "SINGLE_TABLE_REALTIME",
  "steps": [
    "FULL",
    "INCREMENTAL"
  ],
  "source": {
    "datasourceId": "mysql-source",
    "tables": [
      {
        "id": "orders",
        "database": "app",
        "table": "orders"
      }
    ]
  },
  "transform": [],
  "sink": {
    "datasourceId": "pg-target",
    "schemaPolicy": {
      "initial": "VERIFY",
      "evolution": {
        "enabled": true,
        "allowDropColumns": false
      }
    },
    "tables": [
      {
        "input": "orders",
        "database": "ods",
        "schema": "public",
        "table": "ods_orders",
        "write": {
          "mode": "UPSERT"
        }
      }
    ]
  },
  "env": {}
}
```

The `schema` string is the target namespace; `schemaPolicy` is a separate object. They must never share a JSON key. There is no duplicated root-level `schemaChangesEnabled`, `allowDropColumns`, `tableSelection`, or `tableMappings`.

### Source-owned fields

`source.datasourceId` is required. `source.tables` is a nonempty array of explicit objects, each with a unique nonblank `id` (stable within this definition), a nonblank `table`, and optional `database` and `schema` namespaces. The first V4 release permits one Source datasource per job and at most 100 explicit one-to-one table routes. A single-table solution requires one element.

For supported MySQL keyless CDC, explicit `keyless: true` opts into the no-physical-primary-key path. `snapshot.chunkKeyColumn` is the snapshot split key and is not a claim of uniqueness or target row identity. The backend verifies source metadata and the supported source/sink combination. The job never writes `source.xa`.

For supported offline incremental jobs, `source.tables[].incremental` owns `mode` (`TIMESTAMP` or `INCREASING`), `column`, and optional `initialValue`, `delaySeconds`, and `lookbackSeconds`. These settings are admitted only with an offline `INCREMENTAL` data step. They are not put in the corresponding Sink table.

### Sink-owned fields

`sink.datasourceId` is required. `sink.tables` is a nonempty array. Each entry requires `input` referencing **exactly one** `source.tables[].id`, target `table` and optional `database`/`schema`, and `write`. The Sink object itself requires exactly one `sink.schemaPolicy` shared by all routes; per-table schema policies are not accepted. V4 initially requires a bijection between source-table IDs and sink inputs: no fan-out, merge, duplicate source binding, or duplicate physical target, and no implicit table discovery.

`write.mode` uses implemented `APPEND`, `UPSERT`, or `RETRACT` options, subject to solution and physical target capability. Optional `write.primaryKeys` explicitly declares the target write identity; it is **not** the snapshot Chunk Key and must pass target-side physical schema/non-null/uniqueness admission. When omitted, the planner uses verified physical target metadata. An explicit `transaction.mode: "CHECKPOINT_XA"` in `write` is required for the supported keyless `RETRACT` protocol; it is not permitted on unrelated modes. The associated XA JDBC options and instance identity are generated on the Sink physical boundary, never under Source. This declaration pins transaction intent; no execution path is allowed to silently upgrade a stored protocol.

`schemaPolicy.initial` is one of `VERIFY` (target already exists and is checked) or `CREATE_OR_ALTER` (explicit initial schema preparation under existing safety checks). `schemaPolicy.evolution.enabled` and `schemaPolicy.evolution.allowDropColumns` are required booleans. DROP COLUMN requires evolution enabled and the existing supported source/target dialect capabilities. The initial preparation policy is distinct from the ongoing Schema Change Event application policy. Initial `CREATE_OR_ALTER` requires the `FULL` data step, matching the current schema-preparation admission; no schema-only or incremental-only DDL is unlocked. The first V4 release uses one Sink-wide policy for every target table. Independent per-route schema preparation/evolution is not supported and per-table policy overrides must be rejected.

### Environment and REST transport

`env: {}` does not grant access to `ExecutionOptions`, `CheckpointingOptions`, or `PipelineOptions`. The effective physical environment is formed from application policy, trusted Task/Instance identity, verified topology, and any future **explicitly authorized** public Env options. It never accepts supplied checkpoint state paths, restore flags, CDC server IDs, writer commit IDs, or credentials.

The **proposed V4 HTTP representation** is a structured JSON object under `parameters`, not a JSON string inside JSON. The generic create/update request uses `parameters` as an object (for example, a server-side JSON tree), while the persistence layer continues to store canonical immutable JSON text and the Task Plugin determines the pinned parameter schema version. A metadata-only update omits `parameters` and retains the current immutable definition version. Detail and immutable-version responses expose the stored parameters as JSON objects along with their pinned `parameterSchemaVersion`, preserving original V2/V3 field contents without pretending they are V4. The request must not accept user-supplied schema versions or silently reinterpret legacy string payloads as V4.

```json
{
  "name": "orders-realtime",
  "taskType": "DATA_SYNC",
  "parameters": {
    "solutionType": "SINGLE_TABLE_REALTIME",
    "steps": [
      "FULL",
      "INCREMENTAL"
    ],
    "source": {
      "datasourceId": "mysql-source",
      "tables": [
        {
          "id": "orders",
          "database": "app",
          "table": "orders"
        }
      ]
    },
    "transform": [],
    "sink": {
      "datasourceId": "pg-target",
      "schemaPolicy": {
        "initial": "VERIFY",
        "evolution": {
          "enabled": true,
          "allowDropColumns": false
        }
      },
      "tables": [
        {
          "input": "orders",
          "schema": "public",
          "table": "ods_orders",
          "write": {
            "mode": "UPSERT"
          }
        }
      ]
    },
    "env": {}
  }
}
```

This is an **explicit HTTP breaking contract** to be implemented in a later PR, not behavior supplied by the current DTO. The V4 activation must test create, update, details, immutable versions and request rejection for malformed or wrong-type `parameters`. List/page summary models do not need complete `parameters` or executable Connector configuration.

### Additional proposed examples

Offline incremental source selection (embedded into an otherwise valid V4 job):

```json
{
  "datasourceId": "mysql-source",
  "tables": [
    {
      "id": "customers",
      "database": "crm",
      "table": "customers",
      "incremental": {
        "mode": "TIMESTAMP",
        "column": "updated_at",
        "initialValue": "2026-10-01T00:00:00",
        "delaySeconds": 0,
        "lookbackSeconds": 0
      }
    }
  ]
}
```

Full offline multi-table fixture (the `source.tables[].id`/`sink.tables[].input` relation is exact):

```json
{
  "solutionType": "DATABASE_OFFLINE",
  "steps": [
    "FULL"
  ],
  "source": {
    "datasourceId": "mysql-source",
    "tables": [
      {
        "id": "customers",
        "database": "crm",
        "table": "customers"
      },
      {
        "id": "orders",
        "database": "crm",
        "table": "orders"
      }
    ]
  },
  "transform": [],
  "sink": {
    "datasourceId": "pg-target",
    "schemaPolicy": {
      "initial": "CREATE_OR_ALTER",
      "evolution": {
        "enabled": false,
        "allowDropColumns": false
      }
    },
    "tables": [
      {
        "input": "customers",
        "schema": "public",
        "table": "ods_customers",
        "write": {
          "mode": "APPEND"
        }
      },
      {
        "input": "orders",
        "schema": "public",
        "table": "ods_orders",
        "write": {
          "mode": "APPEND"
        }
      }
    ]
  },
  "env": {}
}
```

Keyless Source and explicit target write identity (a selected subset of a V4 job):

```json
{
  "source": {
    "datasourceId": "mysql-source",
    "tables": [
      {
        "id": "events",
        "database": "app",
        "table": "events",
        "keyless": true,
        "snapshot": {
          "chunkKeyColumn": "event_id"
        }
      }
    ]
  },
  "sink": {
    "datasourceId": "mysql-target",
    "schemaPolicy": {
      "initial": "VERIFY",
      "evolution": {
        "enabled": false,
        "allowDropColumns": false
      }
    },
    "tables": [
      {
        "input": "events",
        "database": "ods",
        "table": "events_copy",
        "write": {
          "mode": "UPSERT",
          "primaryKeys": [
            "event_id"
          ]
        }
      }
    ]
  }
}
```

Keyless exact-row retraction uses the same explicit keyless Source but the Sink table contains `"write": {"mode":"RETRACT","transaction":{"mode":"CHECKPOINT_XA"}}` and no `write.primaryKeys`. It remains limited to the existing supported MySQL-to-MySQL transactional path and its recovery guarantees; setting these fields alone does not make unsupported cross-database XA possible.

## Proposed Changes

### Responsibility boundaries

1. **Editor and presentation.** The editor keeps named Basic Info, Source, Transform (read-only until implemented), Sink, and advanced Env sections. Source contains datasource selection, table selection, snapshot and incremental read settings. Sink contains datasource selection, target table mapping, write identity/transaction controls, initial DDL, schema evolution, and DDL preview. Cron/Schedule remains a distinct Task platform lifecycle. Editor state should mirror V4 component boundaries instead of reassembling multiple flat representations.
2. **Definition read.** A version-aware reader extracts persisted fields for details. The Task list is a projection of authorized identity/status/summary and must not execute Connector admission validation to display a stored row. Unreadable definitions retain identity and an actionable configuration error without being treated as successful empty results or permitted runtime actions.
3. **Static parse and validation.** Data Sync Plugin validates JSON/version, required and unknown keys, supported solution/step combinations, component references, duplicate IDs/paths, and conditional options. Component-owned `Option`/`OptionRule` is authoritative; the UI may consume capability metadata but must not become the security decision-maker.
4. **Resource validation.** Workspace access and datasource/table namespace resolution are checked at the authenticated resource boundary. SQL metadata and schema checks are read-only in preview and may fail if the physical database changes.
5. **Planning and physical creation.** `JobPlanner` resolves and pins allowed source/target metadata, chosen row identity, write compatibility and exclusive claims. It produces per-table plans and effective Source/Sink inputs. `PipelineBuilder` uses Source/Sink Factories and an execution-configuration owner to build the physical graph. Connector code owns its own runtime-specific keys, defaults, and option conversion; `JobConfig` must not manually construct CDC/JDBC-specific physical options.
6. **Execution.** The actual admitted Instance rechecks resource revisions and execution safety. Runtime creates instance-scoped checkpoints, CDC replication identities, and transaction state. Neither a saved V4 job nor editor fields can substitute for Runtime recovery evidence.

### Admissible execution patterns

The first V4 activation preserves already implemented solution capabilities:

| Product solution | Scope | V4 steps | Key write constraints |
| --- | --- | --- | --- |
| `SINGLE_TABLE_OFFLINE` | One explicit route | `FULL`; `INCREMENTAL`; or both where offered | `APPEND` for supported full loads, `UPSERT` required for offline incremental |
| `DATABASE_OFFLINE` | Multiple explicit one-to-one routes | Same allowed offline combinations | No duplicate targets; target-specific write intent but currently shared execution parameters |
| `SINGLE_TABLE_REALTIME` | One explicit route | `FULL`, `INCREMENTAL` together | Changelog-aware `UPSERT`; supported keyless variants only with verified identity/protocol |
| `DATABASE_REALTIME` | Multiple explicit one-to-one routes | `FULL`, `INCREMENTAL` together | Current destination dialect and keyless restrictions remain; no new per-table transaction claims |

Schema preparation is requested by `sink.schemaPolicy.initial`, **not** a second `SCHEMA` value in `steps`. The execution report may still show a derived SCHEMA stage. The current product capability catalog determines which selections are legal; unsupported combinations must fail closed instead of falling back to offline or a less reliable write.

### Source, Sink and Env ownership

| Concern | V4 owning path | Runtime owner |
| --- | --- | --- |
| Source datasource and namespace | `source.datasourceId`, `source.tables[]` | DataSource lookup, Source Factory |
| Keyless opt-in and snapshot split key | `source.tables[].keyless`, `.snapshot.chunkKeyColumn` | MySQL CDC Source |
| Offline incremental range | `source.tables[].incremental` | Offline incremental planner, JDBC Source |
| Target table and explicit write identity | `sink.tables[].table`, `.write.primaryKeys` | Target metadata validator, JDBC Sink |
| Write mode and transaction semantics | `sink.tables[].write` | JDBC Sink writer/committer with Checkpoint |
| Initial/ongoing schema handling | `sink.schemaPolicy` | Schema planner/applier/coordinator |
| Physical mode, parallelism and checkpoint | Not writable in V4 `env` | Task execution configuration and YakFlow Runtime |
| Schedule/Cron, debug, task name, Instance identity | Outside V4 parameters | Task platform and Schedule |

### Cross-component constraints and future extension

A Sink referencing a Source output is a constrained mapping, not a general graph. `transform: []` is mandatory and routing must remain one-to-one until a separately accepted proposal establishes transform semantics, repartitioning, row-type changes, state compatibility, and ordering. Unsupported Source/Sink connector types must not be introduced merely by changing a `solutionType` string. Extra Source/Sink/Env property bags, unvalidated raw JDBC SQL, and physical Connector option injection are rejected.

The V4 public contract establishes stable component ownership and the ability to add future supported options through their owner. It does **not** promise dynamic schema/table discovery, arbitrary transforms, per-route independent checkpoint intervals, operator-parallel CDC sinks, or a general DAG engine.

## Compatibility, Deprecation, and Migration Plan

- The current deployed `parameterSchemaVersion=3` remains authoritative until a separate, reviewed activation PR. This **proposal PR must not change** existing parsers, API payloads, migrations, editor output, scheduled runs, or persisted definitions.
- V2/V3 immutable Definition Versions remain tagged with their original versions. Read them by the pinned supported contract for display and, where still admitted, execution. A V3 `source.xa` marker must not be silently interpreted as a V4 Sink transaction configuration. Unsupported historical protocols are visible but fail execution/publish admission with a clear reason.
- New definitions are written as V4 only after implementation and acceptance. Existing saved versions are not automatically overwritten, reserialized, re-published, or assigned a higher parameter version. Explicit migration would require its own operator-visible safety check and new immutable version; it must not mutate an active Instance.
- Existing Task Instance checkpoints, Operator UIDs, JDBC XA recovery/committer records, CDC offsets and state serializers remain unchanged by this proposal. A V4 configuration shape alone provides no proof of restore compatibility. Running jobs retain their pinned definitions; no transparent V3/V4 checkpoint adoption is authorized.
- Current `SCHEMA` is meaningful in V3 `steps`. V4 stores initial schema behavior under Sink only. Projection of an existing V3 definition can display the original step without rewriting it; any future explicit conversion must prove the initial schema intent maps exactly.
- Existing Workspace-scoped datasource authorization, credential separation, target resource claims, and release Flyway history are preserved. No new DB migration is proposed for this format decision because generic Task Versions already record schema versions and JSON content.

## Test Plan

### Contract vectors, before activation

Include full canonical JSON fixtures for single-table realtime, single-table offline full load, offline incremental, multi-table offline and realtime, keyless verified-target-key UPSERT, and keyless MySQL RETRACT with explicit checkpoint XA. Parser/serializer round-trip tests must preserve field meaning, array order, table IDs, schema namespace, initial schema policy, and transaction intent. Contract fixture tests must be executable in the implementation PR; examples in this YOIP are proposal-only.

Negative fixtures must reject: wrong parameter version; missing, extra or duplicate top-level keys; `transform` nonempty; `env` nonempty or privileged state keys; duplicate Source IDs or Sink inputs; missing input references; duplicate targets; multi-table payload under a single-table solution; conflicting schema policies in an unsupported shared-runtime job; `SCHEMA` in V4 steps; missing target write identity for a required UPSERT; `chunkKeyColumn` used as an implicit target PK; XA under Source; RETRACT without explicit `CHECKPOINT_XA`; XA on UPSERT; unsupported target dialect; and credential or CDC checkpoint field injection.

### Lifecycle and service acceptance

- List, detail, create, edit, publish, DDL preview and execute use the correct progressively stronger validation boundary; malformed stored parameters never erase other paged rows.
- Any REST transport change gets request/response JSON type tests; old request shapes do not become accepted implicitly. New V4 Definition Versions are pinned once, while metadata-only edits preserve the immutable parameters/version.
- Verify selecting different Source/Sink connectors, Workspace switching, missing permissions, dropped/renamed tables, revised datasource configurations, table schema divergence, and true backend API failures.
- Check a source table with no physical PK, duplicated source rows, explicit snapshot Chunk Key, target-only verified unique key, keyless retractions, and incompatible target metadata. Test positive and negative modes without weakening the current runtime safety gate.
- Real-database MySQL/JDBC/CDC cases cover initial snapshot, incremental stream, multi-table routing, initial schema creation, schema evolution, DROP COLUMN denial/opt-in, XA commit/abort and checkpoint recovery. Validate no duplicate or missing changes at the claimed delivery guarantee. Tests must distinguish execution behavior from only static JSON assertions.
- Exercise editor draft restore, Source/Sink dependent resets, invalid form controls, mixed-version lists, no new silent compatibility branches, and stable Schedule behavior.
- Run frontend format/lint/types/architecture/contracts/build, targeted backend unit tests, actual database acceptance, and relevant release/checkpoint gates. Report actual run evidence per PR; this proposal does not itself claim runtime test coverage.

## Rejected Alternatives

- **Rename the existing V3 fields in place.** Reinterpreting pinned persisted JSON changes immutable meaning and breaks old definitions, Instance recovery, and client expectations.
- **Expose arbitrary SeaTunnel/HOCON configuration.** Yak Ops owns authenticated Workspace datasource references, task lifecycle, and operator-managed runtime state. Free-form plugin maps would bypass that ownership and validation.
- **Keep `tableMappings` while adding fully fledged `source.tables` and `sink.tables`.** It stores duplicate authoritative table identity and creates conflicting edit paths.
- **Duplicate schema preparation in both root `steps` and Sink.** A separate SCHEMA job step conflicts with an independent per-target initial policy. Derive the presentation/execution stage from Sink policy instead.
- **Move XA into Source or select it from an undocumented boolean default.** Transaction protocol is a Sink/Checkpoint commit concern and must be explicit in persisted intent and admission.
- **Allow nonempty Env or transform options as a forward-compatibility bag.** Those options have no currently validated physical consumer; rejecting them is safer and keeps the first V4 contract narrow.
- **Build a generic form engine, new abstract configuration runtime, or alternate Task API first.** Existing typed Options, Factories, solution catalog, Task Definition versioning and editor components can carry this contract through focused implementation PRs after the design decision.
