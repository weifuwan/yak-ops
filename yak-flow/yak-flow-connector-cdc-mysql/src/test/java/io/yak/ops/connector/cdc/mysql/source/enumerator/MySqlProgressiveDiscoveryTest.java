package io.yak.ops.connector.cdc.mysql.source.enumerator;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Progressive discovery must preserve earlier ranges and their completed watermarks across recovery. */
class MySqlProgressiveDiscoveryTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void restoresDiscoveryPositionWithoutReplanningCompletedChunks() throws Exception {
        var original = assigner();
        var first = MySqlSnapshotSplit.typed("table-0-chunk-0", "fp", range(null, 10L));
        original.appendPlan(first, 1);
        assertEquals(first, original.nextSnapshot());
        original.finishSnapshot(finished(first, 150));
        assertFalse(original.snapshotComplete());
        var serializer = new MySqlHybridEnumeratorStateSerializer();
        var saved = serializer.deserialize(serializer.getVersion(), serializer.serialize(original.snapshotState(1)));
        var restored = new MySqlHybridSplitAssigner("fp", saved);
        assertEquals(0, restored.nextTableIndex());
        assertEquals(1, restored.nextChunkIndex());
        assertEquals(range(10L, null), restored.nextChunkRange());
        assertNull(restored.nextSnapshot());
        assertEquals(java.util.List.of(finished(first,150)), restored.finishedSplits());

        var last = MySqlSnapshotSplit.typed("table-0-chunk-1", "fp", range(10L, null));
        restored.appendPlan(last, 1);
        assertEquals(last, restored.nextSnapshot());
        restored.finishSnapshot(finished(last,250));
        assertTrue(restored.snapshotComplete());
        restored.captureHigh(offset(250));
        restored.snapshotState(3);
        restored.snapshotState(4);
        assertTrue(restored.notifyCheckpointComplete(3), "A later pending checkpoint must not hide an earlier covering completion");
        assertEquals(MySqlHybridEnumeratorState.Phase.STREAMING, restored.snapshotState(5).phase());
    }

    @Test
    void rejectsWrongRangeAndConflictingCompletionWithoutChangingAccounting() {
        var assigner = assigner();
        var split = MySqlSnapshotSplit.typed("chunk", "fp", range(null, null));
        assigner.appendPlan(split, 1);
        assigner.nextSnapshot();
        assertThrows(IllegalArgumentException.class, () -> assigner.finishSnapshot(
                new FinishedSnapshotSplitInfo(TABLE, "chunk", null, null, offset(200), range(1L, null))));
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
        byte[] bytes = serializer.serialize(assigner.snapshotState(1));
        assertThrows(IOException.class, () -> serializer.deserialize(1,bytes));
    }

    private static MySqlHybridSplitAssigner assigner() {
        var assigner = new MySqlHybridSplitAssigner("fp",null);
        assigner.nextBinlog(0);
        assigner.captureLow(offset(100));
        return assigner;
    }
    private static FinishedSnapshotSplitInfo finished(MySqlSnapshotSplit split,long high) {
        return new FinishedSnapshotSplitInfo(TABLE,split.splitId(),null,null,offset(high),split.chunkRange());
    }
    private static ChunkRange range(Long lower, Long upper) {
        return new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, lower, upper);
    }

    private static BinlogOffset offset(long position) {
        return new BinlogOffset(Map.of("server","shop"),Map.of("file","mysql-bin.000001","pos",position));
    }
}
