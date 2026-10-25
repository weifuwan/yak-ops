# Keyless MySQL CDC: product admission

This document records the initial, deliberately narrow, public admission for
physically keyless real-time synchronization. It does not relax keyed CDC.

## Supported path

- `SINGLE_TABLE_REALTIME`, steps `FULL` + `INCREMENTAL`.
- Source: MySQL table with **no physical primary key**.
- Target: MySQL InnoDB table with an exactly compatible full-row projection, no
  physical primary/unique keys, and no unsupported triggers, foreign keys,
  generated fields, or native comparison types.
- Sink: `RETRACT`, using one-row native null-safe comparison and transactionally
  committed, replay-stable receipts.
- Multi-table keyless tasks, Oracle/PostgreSQL product admission, keyless
  runtime schema changes, and initial structure-creation tasks remain disabled.

## Task definition

The public version-three task mapping supplies `keyless` and
`chunkKeyColumn` on **the source table**, not on the datasource connection
and not as JDBC target column metadata:

```json
{
  "solutionType": "SINGLE_TABLE_REALTIME",
  "steps": ["FULL", "INCREMENTAL"],
  "schemaChangesEnabled": false,
  "source": { "datasourceId": "source-mysql" },
  "sink": { "datasourceId": "target-mysql" },
  "tableSelection": { "mode": "EXPLICIT" },
  "tableMappings": [{
    "source": { "database": "app", "table": "events", "keyless": true, "chunkKeyColumn": "bucket" },
    "target": { "database": "ods", "table": "events" },
    "writeMode": "RETRACT"
  }]
}
```

The Chunk Key selects snapshot ranges; it **does not** identify a row.
It must be an existing non-null numeric field with a supported logical type.
Multiple physically identical rows must retain their multiplicity.
Moving the Chunk Key between snapshot ranges and keyless schema changes are
intentionally rejected rather than silently risking lost or duplicate rows.

## Database preparation

Source MySQL must meet the existing CDC replication preflight, including
`binlog_format=ROW`, `binlog_row_image=FULL`, and empty
`binlog_row_value_options`. Consistent snapshot acquisition temporarily
requires a global read lock while the Low watermark and repeatable-read view
are anchored. The replication identity and lock privileges must be provisioned.

Before starting the Job, create the target receipt table on the destination
MySQL database:

```sql
CREATE TABLE yak_flow_cdc_receipts (
    receipt_key BINARY(32) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
```

The receipt table is persistent runtime state. It must **not** be automatically
created, truncated, or removed when a Job restarts or checkpoints replay; it
must remain in the same target database/transaction boundary as the row writes.
See `docs/engineering/cdc/mysql-keyless-transactional-replay.md`.

The JDBC target writer validates physical schema, engine, constraints and
receipt-table contract at startup. Unsafe target shapes fail closed.

## Recovery and acceptance

The keyless Hybrid Source freezes a complete normalized snapshot chunk before
emitting rows. Low/read-view consistency, per-chunk High watermark filtering,
stable row-image identity, and durable sink receipts must remain aligned through
Checkpoint and restart.

The real-MySQL `MySqlHybridIT` keyless test covers initial snapshot, exact
duplicate multiplicity, UPDATE/DELETE, a target commit with an intentionally
older Source checkpoint, replay, and a subsequent DELETE after restart.

Required completion gates: passing Java/Frontend quality, existing JDBC and CDC
regressions, and real database CDC/pipeline/recovery tests. Do not mark the PR
production-ready while any of these checks fail.

## Current limits

- No claim of unrestricted or distributed exactly-once semantics, especially
  across multiple targets or table routes.
- No keyless DDL change handling; mutable Chunk Key crossing split ranges fails.
- The bounded distinct-row-image buffer and serialized checkpoint-state
  constraints still apply; an oversized high-cardinality equal-Chunk-Key range
  fails instead of dropping records.
- Text/collation, floating-point, timezone-sensitive and otherwise unverified
  full-row comparisons are not accepted until database-specific tests prove
  equivalence.
