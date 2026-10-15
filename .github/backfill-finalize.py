from pathlib import Path
C=Path('yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/source')
T=Path('yak-flow/yak-flow-connector-cdc-mysql/src/test/java/io/yak/ops/connector/cdc/mysql/source')
def edit(p,a,b):
    s=p.read_text()
    if a not in s: raise AssertionError(f'Missing anchor in {p}: {a[:100]}')
    p.write_text(s.replace(a,b))
def put(p,s):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(s.strip()+'\n')

p=C/'split/MySqlHybridBinlogSplit.java'
edit(p, '''that point while snapshot ranges are emitted. STREAMING reads from the anchor after
 * a completed snapshot checkpoint. No Binlog offset advances while paused.''', '''bootstrap history while normalized snapshot ranges are checkpointed and emitted.
 * STREAMING starts at the minimum completed chunk High and filters each key by its
 * chunk's High. No Binlog offset advances while paused.''')
edit(p, '        this.highWatermark = highWatermark;', '''        if (phase == Phase.STREAMING) {
            if (highWatermark == null || this.finishedSplits.isEmpty()) {
                throw new IllegalArgumentException("Streaming Hybrid state requires completed chunk watermarks");
            }
            var ids = new java.util.HashSet<String>();
            BinlogOffset maximum = null;
            for (FinishedSnapshotSplitInfo info : this.finishedSplits) {
                if (!ids.add(info.splitId())) throw new IllegalArgumentException("Duplicate streaming chunk identity");
                BinlogOffsetOrder.compare(binlog.offset(), info.highWatermark());
                if (maximum == null || BinlogOffsetOrder.compare(info.highWatermark(), maximum) > 0) {
                    maximum = info.highWatermark();
                }
            }
            if (BinlogOffsetOrder.compare(highWatermark, maximum) != 0) {
                throw new IllegalArgumentException("Hybrid handoff High differs from completed chunk metadata");
            }
        } else if (highWatermark != null || !this.finishedSplits.isEmpty()) {
            throw new IllegalArgumentException("Unfinished Hybrid state must not contain streaming metadata");
        }
        this.highWatermark = highWatermark;''')
p=C/'reader/MySqlHybridSplitState.java'
edit(p, 'Mailbox-confined Snapshot key and Binlog phase/offset for one active Source split.', 'Mailbox-confined normalized snapshot output, preparation checkpoint, and Binlog phase/offset.')
edit(p, '''        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.STREAMING) {
            return;
        }''', '''        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.STREAMING) {
            if (!Objects.equals(high, binlog.highWatermark()) || !finished.equals(binlog.finishedSplits())) {
                throw new IllegalStateException("Restored streaming metadata conflicts with coordinator handoff");
            }
            return;
        }''')
p=C/'enumerator/MySqlHybridEnumeratorState.java'
edit(p, 'import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;', 'import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;\nimport io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;')
edit(p, '        if (binlogAssigned == (pendingBinlog != null)', '''        if (phase == Phase.HANDOFF || phase == Phase.STREAMING) {
            if (!snapshotPlanned || totalSnapshotSplits == 0 || finishedSnapshots.size() != totalSnapshotSplits
                    || highWatermark == null || lowWatermark == null) {
                throw new IllegalArgumentException("Hybrid handoff requires all normalized chunks to be completed");
            }
            BinlogOffset maximum = finishedSplitInfos.stream().map(FinishedSnapshotSplitInfo::highWatermark)
                    .max(BinlogOffsetOrder::compare).orElseThrow();
            if (BinlogOffsetOrder.compare(highWatermark, maximum) != 0
                    || BinlogOffsetOrder.compare(lowWatermark, highWatermark) > 0) {
                throw new IllegalArgumentException("Hybrid coordinator watermarks differ from completed chunks");
            }
        }
        if (binlogAssigned == (pendingBinlog != null)''')

put(T/'enumerator/MySqlProgressiveDiscoveryTest.java', '''
package io.yak.ops.connector.cdc.mysql.source.enumerator;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Progressive discovery must preserve earlier ranges and their completed watermarks across recovery. */
class MySqlProgressiveDiscoveryTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void restoresDiscoveryPositionWithoutReplanningCompletedChunks() throws Exception {
        var original = assigner();
        var first = new MySqlSnapshotSplit("table-0-chunk-0", "fp", TABLE, null, 10L, null);
        original.appendPlan(first, 0, 1, 10L, false);
        assertEquals(first, original.nextSnapshot());
        original.finishSnapshot(finished(first, 150));
        assertFalse(original.snapshotComplete());
        var serializer = new MySqlHybridEnumeratorStateSerializer();
        var saved = serializer.deserialize(serializer.getVersion(), serializer.serialize(original.snapshot(1)));
        var restored = new MySqlHybridSplitAssigner("fp", saved);
        assertEquals(0, restored.nextTableIndex());
        assertEquals(1, restored.nextChunkIndex());
        assertEquals(10L, restored.nextChunkLower());
        assertNull(restored.nextSnapshot());
        assertEquals(java.util.List.of(finished(first,150)), restored.finishedSplits());

        var last = new MySqlSnapshotSplit("table-0-chunk-1", "fp", TABLE, 10L, null, null);
        restored.appendPlan(last, 1, 0, null, true);
        assertEquals(last, restored.nextSnapshot());
        restored.finishSnapshot(finished(last,250));
        assertTrue(restored.snapshotComplete());
        restored.captureHigh(offset(250));
        restored.snapshot(3);
        restored.snapshot(4);
        assertTrue(restored.completeCheckpoint(3), "A later pending checkpoint must not hide an earlier covering completion");
        assertEquals(MySqlHybridEnumeratorState.Phase.STREAMING, restored.snapshot(5).phase());
    }

    @Test
    void rejectsWrongRangeAndConflictingCompletionWithoutChangingAccounting() {
        var assigner = assigner();
        var split = new MySqlSnapshotSplit("chunk", "fp", TABLE, null, null, null);
        assigner.appendPlan(split, 1, 0, null, true);
        assigner.nextSnapshot();
        assertThrows(IllegalArgumentException.class, () -> assigner.finishSnapshot(
                new FinishedSnapshotSplitInfo(TABLE, "chunk", 1L, null, offset(200))));
        assertFalse(assigner.snapshotComplete());
        assigner.finishSnapshot(finished(split,200));
        assigner.finishSnapshot(finished(split,200));
        assertThrows(IllegalStateException.class, () -> assigner.finishSnapshot(finished(split,201)));
        assertEquals(1, assigner.finishedSplits().size());
        assertTrue(assigner.snapshotComplete());
    }

    @Test
    void oldEnumeratorFormatIsRejectedRatherThanReinitialized() throws Exception {
        var assigner = assigner();
        var serializer = new MySqlHybridEnumeratorStateSerializer();
        byte[] bytes = serializer.serialize(assigner.snapshot(1));
        assertThrows(IOException.class, () -> serializer.deserialize(1,bytes));
    }

    private static MySqlHybridSplitAssigner assigner() {
        var assigner = new MySqlHybridSplitAssigner("fp",null);
        assigner.nextBinlog(0);
        assigner.captureLow(offset(100));
        return assigner;
    }
    private static FinishedSnapshotSplitInfo finished(MySqlSnapshotSplit split,long high) {
        return new FinishedSnapshotSplitInfo(TABLE,split.splitId(),split.lowerInclusive(),split.upperExclusive(),offset(high));
    }
    private static BinlogOffset offset(long position) {
        return new BinlogOffset(Map.of("server","shop"),Map.of("file","mysql-bin.000001","pos",position));
    }
}
''')

p=T/'split/MySqlHybridBinlogSplitTest.java'
s=p.read_text(); s=s.rsplit('}',1)[0]+'''
    @org.junit.jupiter.api.Test
    void refusesStreamingStateWithoutMatchingCompletedChunkMetadata() {
        var binlog = new MySqlBinlogSplit("fp", offset(200L), new byte[]{1});
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MySqlHybridBinlogSplit(binlog, MySqlHybridBinlogSplit.Phase.STREAMING, offset(200L)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MySqlHybridBinlogSplit(binlog, MySqlHybridBinlogSplit.Phase.STREAMING,
                        offset(300L), completed(offset(200L))));
    }
}
'''; p.write_text(s)
p=T/'MySqlCdcSourceTest.java'
s=p.read_text(); s=s.rsplit('}',1)[0]+'''
    @org.junit.jupiter.api.Test
    void reservesDifferentReplicationConnectionsAndRejectsOverflow() {
        var table = new io.yak.ops.core.data.TableId("shop",null,"orders");
        var schema = new io.yak.ops.core.types.TableSchema(java.util.List.of(
                new io.yak.ops.core.types.Column("id", io.yak.ops.core.types.LogicalTypes.BIGINT.copy(false))),java.util.List.of("id"));
        var config = new MySqlCdcSourceConfig("localhost",3306,"user","password",1001,"topic",java.util.Map.of(table,schema),256);
        var ids = new java.util.HashSet<Integer>();
        ids.add(config.serverId());
        for(int i=0;i<16;i++) org.junit.jupiter.api.Assertions.assertTrue(ids.add(config.snapshotServerId(i)));
        var overflow = new MySqlCdcSourceConfig("localhost",3306,"user","password",Integer.MAX_VALUE,"topic",java.util.Map.of(table,schema),256);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->overflow.snapshotServerId(0));
    }
}
'''; p.write_text(s)
p=Path('yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/test/java/io/yak/ops/plugin/task/datasync/plan/MySqlCdcReplicationIdentityTest.java')
s=p.read_text(); s=s.rsplit('}',1)[0]+'''
    @org.junit.jupiter.api.Test
    void allocatedReplicationBlocksCannotPartiallyOverlap() {
        var blocks = new java.util.TreeSet<Integer>();
        for(int i=0;i<10000;i++) {
            int base = MySqlCdcReplicationIdentity.serverId("instance-"+i);
            org.junit.jupiter.api.Assertions.assertEquals(1,base%32);
            org.junit.jupiter.api.Assertions.assertTrue(base>0 && base<=Integer.MAX_VALUE-16);
            blocks.add(base);
        }
        Integer previous=null;
        for(int base:blocks) {
            if(previous!=null) org.junit.jupiter.api.Assertions.assertTrue(base-previous>=32);
            previous=base;
        }
        // Complete block collisions remain subject to the existing atomic admission claim.
        org.junit.jupiter.api.Assertions.assertEquals(MySqlCdcReplicationIdentity.exclusiveResource("Aa"),
                MySqlCdcReplicationIdentity.exclusiveResource("BB"));
    }
}
'''; p.write_text(s)

put(Path('docs/yoips/YOIP-6-mysql-chunk-backfill-checkpoints.md'), '''
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
''')
