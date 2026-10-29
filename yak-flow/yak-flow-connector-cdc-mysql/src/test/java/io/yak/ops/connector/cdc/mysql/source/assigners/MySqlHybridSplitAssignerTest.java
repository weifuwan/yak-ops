package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flink CDC 3.7's Snapshot-first Hybrid assignment and recoverable Binlog handoff contract. */
class MySqlHybridSplitAssignerTest {

    @Test
    void assignsBinlogOnlyAfterSnapshotCheckpoint() {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var split = snapshot();
        assertTrue(assigner.getNext().isEmpty());
        assigner.plan(List.of(split));
        assertEquals(split, assigner.getNext().orElseThrow());
        assertTrue(assigner.getNext().isEmpty());
        finish(assigner, split, 300);
        assertTrue(assigner.getNext().isEmpty());
        assigner.snapshotState(42);
        assertTrue(assigner.getNext().isEmpty());
        assigner.notifyCheckpointComplete(41);
        assertTrue(assigner.getNext().isEmpty());
        assigner.notifyCheckpointComplete(42);
        MySqlBinlogSplit binlog = (MySqlBinlogSplit) assigner.getNext().orElseThrow();
        assertEquals(offset(300), binlog.offset());
        assertArrayEquals(new byte[] {1, 2, 3}, binlog.schemaHistory());
        assertEquals(1, binlog.finishedSplits().size());
        assertTrue(assigner.getNext().isEmpty());
    }

    @Test
    void createsIncompleteInitialBinlogWhenMetadataExceedsGroupSize() {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null, 1);
        var first = snapshot();
        var second = new MySqlSnapshotSplit("snapshot-1", first.tableId(), null, null, null);
        assigner.plan(List.of(first, second));
        assigner.getNext().orElseThrow();
        assigner.getNext().orElseThrow();
        finish(assigner, first, 200);
        finish(assigner, second, 300);
        assigner.snapshotState(7);
        assigner.notifyCheckpointComplete(7);
        var binlog = (MySqlBinlogSplit) assigner.getNext().orElseThrow();
        assertFalse(binlog.isCompletedSplit());
        assertEquals(2, binlog.getTotalFinishedSplitSize());
        assertTrue(binlog.finishedSplits().isEmpty());
        assertEquals(offset(200), binlog.offset());
        var completed = binlog.withFinishedSnapshotSplitInfos(assigner.finishedSplits());
        assertTrue(completed.isCompletedSplit());
        assertEquals(2, completed.finishedSplits().size());
    }

    @Test
    void restoresEarliestChunkHistoryBeforeAllocatingBinlog() throws Exception {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var first = snapshot();
        var second = new MySqlSnapshotSplit("snapshot-1", first.tableId(), null, null, null);
        assigner.plan(List.of(first, second));
        assertEquals(first, assigner.getNext().orElseThrow());
        assertEquals(second, assigner.getNext().orElseThrow());
        // Reports may arrive in reverse temporal order.
        finish(assigner, second, 400);
        finish(assigner, first, 300);
        var ser = new PendingSplitsStateSerializer();
        var state = assigner.snapshotState(10);
        var restored = new MySqlHybridSplitAssigner(
                StartupOptions.initial(),
                (HybridPendingSplitsState) ser.deserialize(ser.getVersion(), ser.serialize(state)));
        assertTrue(restored.getNext().isEmpty());
        restored.snapshotState(11);
        restored.notifyCheckpointComplete(11);
        var binlog = (MySqlBinlogSplit) restored.getNext().orElseThrow();
        assertEquals(offset(300), binlog.offset());
        assertArrayEquals(new byte[] {1, 2, 3}, binlog.schemaHistory());
        assertEquals(2, binlog.finishedSplits().size());
    }

    @Test
    void restoredInitialHandoffRetainsHistoryOffsetWhenEarliestTableWasRemoved() throws Exception {
        var restored = restoreBeforeBinlogWithRemovedTable(true);
        assertEquals(1, restored.finishedSplits().size());
        assertEquals(offset(200), restored.high());
        var binlog = (MySqlBinlogSplit) restored.getNext().orElseThrow();
        // History was captured at A's HIGH=100, and cannot be safely moved to
        // B's HIGH=200: DDL between those positions must still be replayed.
        assertEquals(offset(100), binlog.offset());
        assertEquals(offset(200), binlog.highWatermark());
        assertEquals("table-b", binlog.finishedSplits().getFirst().splitId());
        assertArrayEquals(new byte[] {1, 2, 3}, binlog.schemaHistory());
        assertTrue(restored.getNext().isEmpty());
    }

    @Test
    void restoredInitialHandoffRecomputesHighWhenLatestTableWasRemoved() throws Exception {
        var restored = restoreBeforeBinlogWithRemovedTable(false);
        assertEquals(1, restored.finishedSplits().size());
        assertEquals(offset(100), restored.high());
        var binlog = (MySqlBinlogSplit) restored.getNext().orElseThrow();
        assertEquals(offset(100), binlog.offset());
        assertEquals(offset(100), binlog.highWatermark());
        assertEquals("table-a", binlog.finishedSplits().getFirst().splitId());
        assertArrayEquals(new byte[] {1, 2, 3}, binlog.schemaHistory());
        assertTrue(restored.getNext().isEmpty());
    }

    /**
     * Restore a completed INITIAL checkpoint after dropping one previously captured
     * table. This must not reset the Debezium history to a later offset.
     */
    private static MySqlHybridSplitAssigner restoreBeforeBinlogWithRemovedTable(boolean removeEarliest)
            throws Exception {
        var tableA = new TableId("shop", null, "orders_a");
        var tableB = new TableId("shop", null, "orders_b");
        var first = new MySqlSnapshotSplit("table-a", tableA, null, null, null);
        var second = new MySqlSnapshotSplit("table-b", tableB, null, null, null);
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assigner.initializeTables(List.of(tableA, tableB), true, false);
        assigner.plan(List.of(first, second));
        assertEquals(first, assigner.getNext().orElseThrow());
        assertEquals(second, assigner.getNext().orElseThrow());
        finish(assigner, first, 100);
        finish(assigner, second, 200);
        var serializer = new PendingSplitsStateSerializer();
        var state = assigner.snapshotState(10);
        assigner.notifyCheckpointComplete(10);
        // Restore the checkpointed coordinator, not the in-memory assigner.
        var restored = new MySqlHybridSplitAssigner(
                StartupOptions.initial(),
                (HybridPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(state)));
        restored.initializeTables(List.of(removeEarliest ? tableB : tableA), true, false);
        return restored;
    }

    @Test
    void returnedBinlogPreservesItsOffsetAndRejectsOldMetadataAck() {
        var assigner = completedAssigner();
        assigner.snapshotState(10);
        assigner.notifyCheckpointComplete(10);
        MySqlBinlogSplit binlog = assigner.nextBinlog();
        assertTrue(binlog != null);
        long previousGeneration = assigner.getBinlogAssignmentGeneration();
        assigner.addBack(binlog);
        assertEquals(previousGeneration + 1, assigner.getBinlogAssignmentGeneration());
        assertFalse(assigner.onBinlogSplitMetaAssembled(previousGeneration));
        assertSame(binlog, assigner.nextBinlog());
        assertTrue(assigner.onBinlogSplitMetaAssembled(assigner.getBinlogAssignmentGeneration()));
    }

    @Test
    void metadataReleaseWaitsForReaderAssemblyAndCheckpoint() throws Exception {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var split = snapshot();
        assigner.initializeTables(List.of(split.tableId()), false, true);
        assigner.plan(List.of(split));
        assigner.nextSnapshot();
        finish(assigner, split, 300);
        assigner.snapshotState(10);
        assigner.notifyCheckpointComplete(10);
        assertTrue(assigner.nextBinlog() != null);
        assigner.snapshotState(11);
        assigner.notifyCheckpointComplete(11);
        assertFalse(assigner.isSnapshotMetaReleased());
        assertTrue(assigner.onBinlogSplitMetaAssembled(assigner.getBinlogAssignmentGeneration()));
        assigner.snapshotState(12);
        assigner.notifyCheckpointComplete(11);
        assertFalse(assigner.isSnapshotMetaReleased());
        assigner.notifyCheckpointComplete(12);
        assertTrue(assigner.isSnapshotMetaReleased());

        var serializer = new PendingSplitsStateSerializer();
        var released = assigner.snapshotState(13);
        var restored = new MySqlHybridSplitAssigner(
                StartupOptions.initial(), (HybridPendingSplitsState) serializer.deserialize(
                        serializer.getVersion(), serializer.serialize(released)));
        assertTrue(restored.isSnapshotMetaReleased());
    }

    @Test
    void unfinishedPlanningCheckpointRestoresWithoutBinlogState() throws Exception {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var ser = new PendingSplitsStateSerializer();
        var state = (HybridPendingSplitsState) ser.deserialize(
                ser.getVersion(), ser.serialize(assigner.snapshotState(1)));
        assertNull(state.pendingBinlog());
        assertFalse(state.isBinlogSplitAssigned());
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), state);
        assertTrue(restored.getNext().isEmpty());
        restored.plan(List.of(snapshot()));
        assertTrue(restored.snapshotPlanned());
    }

    private static MySqlHybridSplitAssigner completedAssigner() {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var split = snapshot();
        assigner.plan(List.of(split));
        assigner.getNext();
        finish(assigner, split, 300);
        return assigner;
    }

    private static void finish(MySqlHybridSplitAssigner assigner, MySqlSnapshotSplit split, long position) {
        var high = offset(position);
        assigner.finishSnapshot(new FinishedSnapshotSplitInfo(
                split.tableId(), split.splitId(), null, null, high));
        assigner.captureSnapshotSchemaHistory(
                Map.of(split.splitId(), high), Map.of(split.splitId(), new byte[] {1, 2, 3}));
        if (assigner.snapshotComplete()) {
            assigner.captureHigh(assigner.finishedSplits().stream()
                    .map(FinishedSnapshotSplitInfo::highWatermark)
                    .max(DebeziumOffset::compareTo).orElseThrow());
        }
    }

    private static MySqlSnapshotSplit snapshot() {
        return new MySqlSnapshotSplit(
                "snapshot-0", new TableId("shop", null, "orders"), null, null, null);
    }

    private static DebeziumOffset offset(long value) {
        return new DebeziumOffset(Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", value));
    }
}
