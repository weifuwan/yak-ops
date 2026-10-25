# Keyless MySQL CDC: product admission

This document records the initial, deliberately narrow, public admission for
physically keyless real-time synchronization. It does not relax keyed CDC.

## Supported path

- `SINGLE_TABLE_REALTIME`, steps `FULL` + `INCREMENTAL`.
- Source: MySQL table with **no physical primary key**.
- Target: MySQL InnoDB table with an exactly compatible full-row projection, no
  physical primary/unique keys, and no unsupported triggers, foreign keys,
  generated fields, or native comparison types.
- Sink: `RETRACT`, using one-row native null-safe comparison and one of two
  explicitly pinned recovery protocols: legacy durable receipts or checkpoint-coordinated
  MySQL XA for new definitions.
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
    "source": { "database": "app", "table": "events", "keyless": true, "chunkKeyColumn": "bucket", "xa": true },
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

**New keyless task definitions** with `tableMappings[].source.xa=true` use a
checkpoint-coordinated XA Committer. The target must be MySQL with
`xa_detach_on_prepare=ON`, a direct JDBC XA connection (no SSH tunnel), and
credentials allowed to recover owned prepared XA transactions. The Runtime requires
a durable checkpoint store. XA PREPARE does not make rows visible: only after the
checkpoint is persisted does the Committer execute XA COMMIT. An unresolvable
or ambiguous XA state blocks execution.

**Existing keyless task definitions** without the `xa` property stay on the
legacy Receipt protocol. This is intentionally a *versioned selection*;
reinterpreting old checkpoint or Receipt state as XA is unsafe. For legacy
definitions the target administrator must still create this table:

```sql
CREATE TABLE yak_flow_cdc_receipts (
    receipt_key BINARY(32) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
```

For legacy Receipt writers, the receipt table is persistent runtime state. It must **not** be automatically
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

The real-MySQL `MySqlXaSinkIT` verifies XA visibility, prepared-transaction
recovery and orphan rollback without a receipt table. The backend E2E
`RealtimeSyncIT` exercises new keyless task publication and a real MySQL-to-MySQL
snapshot and Binlog pipeline. These checks are distinct from the existing
`MySqlHybridIT` Receipt regression, which covers initial snapshot, exact
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

## Migration and operational safety

- Never automatically delete or truncate `yak_flow_cdc_receipts`, since historical
  checkpoints may still refer to it.
- Switching an existing running task from Receipt to XA is not an in-place upgrade:
  require a clean task boundary, independently validated target state, and a new
  execution instance rather than interpreting an old checkpoint with a new protocol.
- Both `XA PREPARE` and orphan recovery need XA-capable database privileges.
  During outages, unresolved prepared XIDs can hold locks. Do not perform
  broad `XA ROLLBACK` of other tasks or applications.
- The initial XA implementation requires the MySQL detached-XA server capability,
  one Writer subtask and the local durable aligned-checkpoint runtime. It does
  not imply a distributed Flink cluster's end-to-end guarantees.
