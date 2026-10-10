# YakFlow MySQL CDC — PR2 Hybrid Snapshot + Binlog Handoff

Status: Connector implementation; real-MySQL Hybrid acceptance must pass before treating the path as verified. PR3 product/JDBC Sink integration remains separate.

## Boundary

The PR1 `MySqlCdcSource.builder().build()` remains an independent stream-only, unbounded Source with its original Binlog split/checkpoint codec. For first-run whole-table data followed by Binlog, choose:

```java
MySqlHybridCdcSource source = MySqlCdcSource.builder()
        .hostname("localhost")
        .username("replication_user")
        .password(password)
        .serverId(5401)
        .topicPrefix("yak_cdc_orders")
        .table(new TableId("shop", null, "orders"), resolvedTableSchema)
        .buildHybrid(2048);
```

This Source is `CONTINUOUS_UNBOUNDED`, **requires periodic durable Checkpoint configuration**, and uses `MySqlHybridSplit` instead of the stream-only `MySqlBinlogSplit`. The modes' checkpoints are not interchangeable.

## Dataflow and recovery

1. Enumerator assigns exactly **one Debezium Binlog split to subtask zero**. It waits for its first valid Binlog offset and complete Debezium Schema History before planning any JDBC Snapshot ranges. This becomes the **global Low Watermark**. The first change is fenced by the subsequent full scan; remaining Binlog changes are held behind bounded Debezium backpressure.
2. `MySqlChunkSplitter` performs background keyset planning. Only exactly one non-null **BIGINT** primary key is supported. Half-open ranges `[lowerInclusive, upperExclusive)` cover the complete keyspace; sparse keys do not create thousands of empty numeric-range splits. Planning has a per-table upper limit of 10,000 chunks.
3. The Enumerator assigns Snapshot ranges to parallel SourceReaders. `MySqlSnapshotSplitReader` uses a distinct caller-owned JDBC connection for each range; ResultSets never leave the fetcher. `MySqlHybridRecordEmitter` advances the last **delivered** primary key on the mailbox only after downstream succeeds. Restored ranges seek `key > lastEmittedKey`.
4. A Reader reports completed ranges to the Enumerator and retains finished-but-unacknowledged splits in its own checkpoint state. Enumerator completion is not inferred from assigning a split or from a delivery ACK.
5. Only after all ranges finish does the Enumerator obtain a global **High Watermark** from MySQL Binlog status. It waits for a completed **aligned checkpoint** covering those snapshot rows and the Binlog low anchor before sending the Resume event.
6. The Binlog reader replays changes from Low (including events during the snapshot). The original single Debezium engine has been paused rather than restarted; on failure the Binlog split restores from the last mailbox-emitted offset plus Schema History. As Binlog catches High, replay transitions into the live stream without dropping intermediate updates or deletes.

### Why this differs from Flink CDC

Flink CDC's `MySqlSnapshotSplitReadTask` and `SnapshotSplitReader` record an individual Low and High Watermark **per Chunk**, backfill those changes into a normalized in-memory snapshot before emitting it, then filter overlap when handing off to the global Binlog split. This PR implements a **more conservative global-fence and post-snapshot ordered Binlog replay** instead. Snapshot rows can temporarily represent different database instants until replay catches High. Both duplicate/replayed operations and reader restart semantics are **at-least-once**, requiring UPSERT/idempotent downstream processing. It does **not** promise Flink's per-chunk normalized snapshot consistency, exactly-once or referential constraints across target-table write order.

## What fails closed

- Missing primary key, composite/non-BIGINT primary key, invalid/changing source definition, unsupported schema changes
- Any snapshot/checkpoint before the Binlog low offset and Schema History are ready, or while metadata planning/high-watermark capture is incomplete
- Missing/purged Binlog and a restart offset that Debezium cannot apply
- Restoring Hybrid state with a different `chunkSize`, schema, table list, host, server ID or topic prefix
- No periodic Checkpoint: Hybrid cannot safely open Binlog until a completed checkpoint covers the snapshot

## Test strategy

Unit tests cover sparse keyset partitioning, half-open ranges, consumed-key seek after restart, binary state codecs, phase transitions and waiting for a completed handoff checkpoint. A separate **manual** real-MySQL test should exercise writes during Snapshot and restart. Basic PR CI remains four Quality Check jobs; this PR does not install another automatic multi-database matrix.

Not supported here: product Task lifecycle, JDBC Sink orchestration, schema evolution, dynamic tables, historical backfill preceding the captured Low Watermark, unsupported source types or XA/Exactly-once.
