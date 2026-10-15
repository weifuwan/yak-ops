# YOIP-6: MySQL Chunk Backfill and Durable Snapshot Output

Status: Discussion

Discussion: https://github.com/weifuwan/yak-ops/pull/1653

Decision: Pending maintainer review of checkpoint compatibility and the implementation evidence.

Implementation: Draft PR #1653, following the approved implementation scope in the task discussion. This document does not claim a maintainer approval or release.

Release: N/A

Related FLIPs: FLIP-27 (Source API ownership and split state).

## Motivation

Raw JDBC snapshots followed by replay from a global Low can expose values older than the snapshot. A per-chunk Low/High backfill normalizes the chunk before its rows are emitted. Filtering later Binlog events requires the matching chunk's High, not a global completion timestamp.

YakFlow's JDBC writer commits before the global checkpoint is complete and is not an XA sink. Merely restarting an unfinished snapshot query is unsafe: a row already committed to the target may have disappeared from the source, and a fresh snapshot plus a later High would omit the correction. The source must retain the normalized result that generated those external writes.

## Public Interfaces

Task REST/JSON, Source/Sink SPI, Flyway and the CDC single-writer restriction are unchanged. Hybrid Source split/enumerator checkpoint formats change to version 2. Version 1 is rejected; the standalone Binlog-only checkpoint format is unchanged. The Hybrid source fingerprint changes with this protocol.

Hybrid configuration reserves seventeen replication IDs: one continuous reader and up to sixteen snapshot backfill readers. The platform assigns aligned blocks of thirty-two IDs and uses its existing atomic claim on the block base. Direct connector callers must reserve their complete range themselves.

## Proposed Changes

Each fetcher owns a JDBC scanner and a bounded Debezium connection. It reads Low, scans one frozen primary-key range, reads High, and consumes ordered Binlog changes through High. Equal SQL boundaries prove an empty interval. Otherwise completion requires an actual Binlog position or heartbeat that reaches High; queue emptiness and elapsed time are never treated as completion.

The source freezes normalized INSERT rows and both watermarks. A Reader checkpoint records this immutable result before any of its rows are emitted. Only a completed checkpoint releases output. The fetcher hands off bounded batches, while the mailbox advances the output index after downstream collection. Restoring a partially emitted chunk replays the retained result rather than rescanning a newer source state. Later changes, including deletions of rows already written, are read from Binlog after the retained High.

Once the final output batch has been emitted, the Reader stores a small completion marker and reports its range and High. It retains that marker until the coordinator acknowledgement is itself covered by a completed checkpoint. The coordinator persists completed metadata and gates the continuous handoff on a checkpoint covering all snapshot output and completion metadata. Continuous Binlog starts at the minimum completed High and filters every key using its own chunk High. Primary-key changes with only one surviving image become a standalone DELETE or INSERT.

Discovery produces one next keyset boundary per asynchronous call and retains at most thirty-two pending chunks. The checkpoint stores the table index, chunk index and next lower bound; already discovered ranges and IDs are not recomputed on restoration. In-memory normalized chunks have explicit row and retained-payload budgets, and their exact serialized representation is checked before publication. Oversized chunks fail without partial normalized output. These bounds are not a claim of exact JVM heap accounting.

The bounded completion metadata remains in checkpoints during streaming. Releasing it after a pure-Binlog checkpoint is an optional follow-up optimization, not a prerequisite for this protocol. It must not be removed merely because the reader is idle.

### Guarantees and limits

Delivery remains at-least-once with primary-key UPSERT convergence. There is no source-transaction-preserving cross-table atomicity or exactly-once claim. Extra unique constraints, foreign keys and side-effecting triggers can still fail under different table or chunk orders. Supported sources have one non-null signed BIGINT chunk key, fixed selected tables and a fixed schema; dynamic table/DDL discovery and parallel CDC sinks are not added.

Retaining normalized output adds a checkpoint round trip before each active chunk is released and increases checkpoint I/O. This is the deliberate cost of safe recovery with a non-transactional end-to-end sink. Removing that gate requires a different proven recovery contract, not a shorter timeout.

## Compatibility, Deprecation, and Migration Plan

Stop old Hybrid tasks and confirm their database replication connections are closed before deploying this version. Mixed old/new replication allocators must not run concurrently: old IDs were allocated individually and may overlap a new block.

Preserve old checkpoint directories for diagnosis or rollback. Version-1 Hybrid checkpoints cannot be converted by inventing missing per-chunk High values or normalized payloads. Restart requires explicit task reinitialization and an operator-approved target reconciliation plan; silently starting from the latest Binlog or applying UPSERT alone to a stale target is not a migration. Do not delete checkpoints, clear destination tables or advance task cursors automatically.

Version-2 checkpoints contain normalized source row values. Store them with the same access restrictions, protected backups and retention controls as source data. Logging remains redacted; this proposal does not add encryption at rest or authorize exposing state payloads in diagnostics. Unsupported scalar state markers and corrupt lengths are rejected; Java object deserialization is not used.

## Test Plan

Unit tests cover immutable scalar round trips, unsupported/old state rejection, preparation and output checkpoint gates, empty chunks, partial output positions, progressive discovery recovery, conflicting completion metadata and replication block allocation. JDBC batching and rollback regressions remain in place.

Docker tests place updates, deletes, inserts and primary-key moves after the snapshot scan and before High. They cover quiet sources, other selected tables, Factory-created Sources with actual JDBC targets, failure after only the first output batch committed, and recovery after the source deletes that already committed row. The existing real YakFlow MySQL/PostgreSQL/Oracle pipelines exercise Runtime checkpoint callbacks and continuous recovery. Full Task E2E and release checks are separate evidence and must be reported independently.

## Rejected Alternatives

- Filter everything before a global High: early chunks can miss changes that their snapshot did not include.
- Recompute normalized rows after partial target writes: a later snapshot can omit a previously emitted row and lose its corrective deletion.
- Hold a JDBC transaction across all tables until checkpoint completion: it changes resource and sink contracts without providing the required end-to-end commit protocol.
- Claim completion after an idle wait: a quiet or backpressured stream does not prove that High was consumed.
- Start a fresh independent Binlog subscription per table with the same replication ID: connections can displace one another and fragment ordering.

## Design provenance

The Low/Snapshot/High/backfill and per-chunk filtering responsibilities were compared with `SnapshotSplitReader`, `BinlogSplitReader`, and `FinishedSnapshotSplitInfo` in the supplied Flink CDC repository. The durable pre-output checkpoint is specific to YakFlow's existing JDBC commit behavior. Debezium cursor and heartbeat behavior is reviewed against the pinned `v2.7.3.Final` source, especially `BinlogOffsetContext` and `BinlogStreamingChangeEventSource`; no latest-version behavior is assumed. Existing Apache notices remain intact, and no additional upstream source is copied by this proposal.
