# YOIP-11: Typed Snapshot Split Assignment and Recovery

Status: PR 2 of keyless CDC execution. **Reader execution remains disabled.**

## Progressive typed discovery

The existing `MySqlHybridSourceEnumerator` now uses two paths:

- Legacy single BIGINT physical primary key: `MySqlChunkSplitter.planNext`, `Long nextChunkLower` and unmodified checkpoint behavior.
- Explicit typed Chunk Key that cannot use the legacy mode:
  `MySqlChunkSplitter.planNextTyped` → `MySqlSnapshotSplit.typed` →
  `MySqlHybridSplitAssigner.appendTypedPlan` with the next typed cursor.

The planner reads **only one range at a time** off the coordinator thread. It
uses the existing database-ordered `nextChunk` operation, which groups equal
values in the same half-open chunk. The next cursor is an immutable
`ChunkRange(table, column, type, previousUpper, null)`. Every planned split
receives a stable ID based on table index and chunk index.

The Enumerator's v3 checkpoint already stores the typed discovery cursor,
pending/assigned splits, finished split watermarks, table index and chunk index.
On restore, the coordinator verifies the typed cursor belongs to the selected
table, column and logical type before issuing any further JDBC discovery. The
splitter also validates the cursor before opening its JDBC connection.
A checkpoint recorded while discovery is in flight contains only previously
committed plans: the uncompleted JDBC callback may safely be recalculated.

## Source of truth and limitations

The production Source Factory still rejects the keyless runtime option introduced
in YOIP-10. `MySqlHybridCdcSource` still enforces the historical BIGINT
physical-PK execution path, and the legacy Snapshot Reader fails closed if a typed
split reaches it. Therefore this change **does not enable a keyless CDC task**.

Tests exercise (1) the actual Enumerator asynchronous assignment/restore
with a typed non-PK DECIMAL column on a keyed test table, and (2) a
**physically keyless** source table via the same typed splitter and
checkpoint-aware assigner. They do not claim live keyless Source execution.

## Acceptance

- Typed ranges remain half-open; duplicate chunk values stay in one chunk.
- A partial discovery checkpoint preserves the exact next decimal value,
  including values beyond the signed 64-bit range.
- A restarted Enumerator does not plan a previously assigned split again.
- A completed split retains the original High watermark and typed range after
  round-trip serialization.
- Wrong table, chunk column, type, missing cursor, and mismatched chunk index
  fail closed before opening JDBC.
- Existing BIGINT split IDs, cursors, source fingerprint and checkpoint bytes
  are unchanged.
- CDC Source and Sink execution gates remain in place until PR 3/4/5/7/8.

Follow-up: PR 3 will make typed Snapshot Reader scanning and recovery executable.
