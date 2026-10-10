package io.yak.ops.connector.cdc.mysql.source.enumerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Proves Binlog handoff needs a completed snapshot checkpoint, not only an assignment ACK. */
class MySqlHybridHandoffTest {

    @Test
    void gatesHandoffAndRestoresCompletedPendingTransition() throws Exception {
        var low = new BinlogOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var high = new BinlogOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 300L));
        var assigner = new MySqlHybridSplitAssigner("fingerprint", null);
        assertEquals(MySqlHybridEnumeratorState.Phase.BOOTSTRAP, assigner.phase());
        assertEquals("mysql-binlog", assigner.nextBinlog(0).splitId());
        assigner.captureLow(low);
        assertThrows(IllegalStateException.class, () -> assigner.snapshot(10));
        var split = new MySqlSnapshotSplit("snap-1", "fingerprint", new TableId("shop", null, "orders"),
                null, null, null);
        assigner.plan(List.of(split));
        assertFalse(assigner.snapshotComplete());
        assertEquals(split, assigner.nextSnapshot());
        assigner.finishSnapshot(split.splitId());
        assertTrue(assigner.snapshotComplete());
        assigner.captureHigh(high);
        assertFalse(assigner.completeCheckpoint(11));
        MySqlHybridEnumeratorState waiting = assigner.snapshot(12);
        assertFalse(assigner.completeCheckpoint(11));
        assertEquals(MySqlHybridEnumeratorState.Phase.HANDOFF, assigner.phase());

        var serializer = new MySqlHybridEnumeratorStateSerializer();
        var checkpoint = serializer.deserialize(serializer.getVersion(), serializer.serialize(waiting));
        var restored = new MySqlHybridSplitAssigner("fingerprint", checkpoint);
        restored.resumeRestoredHandoff();
        assertEquals(MySqlHybridEnumeratorState.Phase.STREAMING, restored.phase());
        assertTrue(assigner.completeCheckpoint(12));
        assertEquals(MySqlHybridEnumeratorState.Phase.STREAMING, assigner.phase());
    }

    @Test
    void rejectsDifferentDefinitionOnRestore() {
        var assigner = new MySqlHybridSplitAssigner("fingerprint", null);
        var state = assigner.snapshot(1);
        assertThrows(IllegalArgumentException.class, () -> new MySqlHybridSplitAssigner("changed", state));
    }
}
