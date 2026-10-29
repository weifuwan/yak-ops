package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Proves Binlog handoff needs a completed snapshot checkpoint, not only an assignment ACK. */
class MySqlHybridSplitAssignerTest {

    /**
     * Flink CDC 3.7 defers the initial Binlog split until the completed snapshot
     * assignment has been covered by a checkpoint. This is the target contract
     * for removing the YakFlow BOOTSTRAP -> HANDOFF -> RESUME protocol.
     */
    @Test
    void assignsBinlogOnlyAfterSnapshotCheckpoint() {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var split = new MySqlSnapshotSplit(
                "snapshot-0", new TableId("shop", null, "orders"), null, null, null);
        var high = new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 300L));

        // A new Hybrid assigner must not allocate the Binlog reader before Snapshot.
        assertTrue(assigner.getNext().isEmpty());
        assigner.plan(List.of(split));
        assertEquals(split, assigner.getNext().orElseThrow());
        assertTrue(assigner.getNext().isEmpty());

        assigner.finishSnapshot(finished(split, 300));
        // Finished reports alone cannot open the stream before a durable checkpoint.
        assertTrue(assigner.getNext().isEmpty());
        assigner.snapshotState(42);
        assertTrue(assigner.getNext().isEmpty());
        assigner.notifyCheckpointComplete(42);

        var binlog = assigner.getNext().orElseThrow();
        assertTrue(binlog instanceof io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit);
        assertEquals(high, ((io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit) binlog).offset());
        assertTrue(assigner.getNext().isEmpty());
    }

    @Test
    void gatesHandoffAndRestoresCompletedPendingTransition() throws Exception {
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var high = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 300L));
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assertEquals(HybridPendingSplitsState.Phase.BOOTSTRAP, assigner.phase());
        assertEquals("mysql-binlog", assigner.nextBinlog().splitId());
        assigner.captureLow(low);
        assertFalse(assigner.snapshotState(10).getSnapshotPendingSplits().snapshotPlanned());
        var split = new MySqlSnapshotSplit("snap-1", new TableId("shop", null, "orders"),
                null, null, null);
        assigner.plan(List.of(split));
        assertFalse(assigner.snapshotComplete());
        assertEquals(split, assigner.nextSnapshot());
        assigner.finishSnapshot(finished(split, 300));
        assertTrue(assigner.snapshotComplete());
        assigner.captureHigh(high);
        // The reader must assemble the entire metadata set before a handoff checkpoint
        // can make the resumed Binlog visible.
        assigner.notifyCheckpointComplete(11);
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());
        HybridPendingSplitsState waiting = assigner.snapshotState(12);
        assigner.notifyCheckpointComplete(11);
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());

        var serializer = new PendingSplitsStateSerializer(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.INSTANCE);
        var checkpoint = (HybridPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(waiting));
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), checkpoint);
        assertThrows(IllegalStateException.class, restored::resumeRestoredHandoff);
        assertTrue(restored.onBinlogSplitMetaAssembled(restored.getBinlogAssignmentGeneration()));
        restored.resumeRestoredHandoff();
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, restored.phase());
        assertTrue(assigner.onBinlogSplitMetaAssembled(assigner.getBinlogAssignmentGeneration()));
        assigner.snapshotState(13);
        assigner.notifyCheckpointComplete(12);
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());
        assigner.notifyCheckpointComplete(13);
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, assigner.phase());
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, assigner.phase());
    }


    @Test
    void staleBinlogMetadataAcknowledgmentsCannotReleaseNewReaderAssignment() {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var high = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 300L));
        var binlog = assigner.nextBinlog();
        assigner.captureLow(low);
        var chunk = new MySqlSnapshotSplit("chunk-0", new TableId("shop", null, "orders"), null, null, null);
        assigner.plan(List.of(chunk));
        assigner.nextSnapshot();
        assigner.finishSnapshot(finished(chunk, 300));
        assigner.captureHigh(high);
        long oldGeneration = assigner.getBinlogAssignmentGeneration();

        assertFalse(assigner.onBinlogSplitMetaAssembled(oldGeneration + 1));
        assigner.snapshotState(20);
        assigner.notifyCheckpointComplete(20);
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());
        assigner.addBack(binlog);
        assertEquals(oldGeneration + 1, assigner.getBinlogAssignmentGeneration());
        assertFalse(assigner.onBinlogSplitMetaAssembled(oldGeneration));
        assertTrue(assigner.nextBinlog() != null);
        assertTrue(assigner.onBinlogSplitMetaAssembled(assigner.getBinlogAssignmentGeneration()));
        assigner.snapshotState(21);
        assigner.notifyCheckpointComplete(21);
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, assigner.phase());
    }

    @Test
    void reportsUpstreamAssignmentStatusWithoutTreatingAssignmentAsCompletion() {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assertEquals(AssignerStatus.INITIAL_ASSIGNING, assigner.getAssignerStatus());
        assertTrue(assigner.waitingForFinishedSplits());
        assertFalse(assigner.noMoreSplits());

        assigner.nextBinlog();
        assigner.captureLow(new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L)));
        var split = new MySqlSnapshotSplit("snap-1", new TableId("shop", null, "orders"), null, null, null);
        assigner.plan(List.of(split));
        assertEquals(split, assigner.getNext().orElseThrow());
        assertTrue(assigner.noMoreSplits());
        assertTrue(assigner.waitingForFinishedSplits());

        assigner.onFinishedSplits(Map.of("snap-1", new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 200L))));
        assertFalse(assigner.waitingForFinishedSplits());
        assigner.captureHigh(new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 200L)));
        assertEquals(AssignerStatus.INITIAL_ASSIGNING, assigner.getAssignerStatus());
        // Assignment completion is durable only after the checkpoint containing all finished
        // Snapshot split metadata completes.
        assigner.snapshotState(22);
        assigner.notifyCheckpointComplete(22);
        assertEquals(AssignerStatus.INITIAL_ASSIGNING_FINISHED, assigner.getAssignerStatus());
    }

    @Test
    void checkpointDuringAsyncSnapshotPlanningRestartsPlanFromDurableState() throws Exception {
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assigner.nextBinlog();
        assigner.captureLow(low);

        // The planning callback has not run yet. A periodic checkpoint must not fail the job.
        var serializer = new PendingSplitsStateSerializer(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.INSTANCE);
        var saved = assigner.snapshotState(21);
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), (HybridPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(saved)));
        assertEquals(HybridPendingSplitsState.Phase.SNAPSHOT, restored.phase());
        assertFalse(restored.snapshotPlanned());
        restored.plan(List.of(new MySqlSnapshotSplit("snap-1", new TableId("shop", null, "orders"), null, null, null)));
        assertTrue(restored.snapshotPlanned());
    }

    @Test
    void restoresCompletedWatermarksBeforeHandoff() throws Exception {
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assigner.nextBinlog();
        assigner.captureLow(low);
        var split = new MySqlSnapshotSplit("snap-1", new TableId("shop", null, "orders"), null, null, null);
        assigner.plan(List.of(split));
        assigner.nextSnapshot();
        assigner.finishSnapshot(finished(split, 200));

        var saved = assigner.snapshotState(22);
        var serializer = new PendingSplitsStateSerializer(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.INSTANCE);
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), (HybridPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(saved)));
        assertEquals(HybridPendingSplitsState.Phase.SNAPSHOT, restored.phase());
        assertTrue(restored.snapshotComplete());
        restored.captureHigh(
                new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 200L)));
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, restored.phase());
    }

    @Test
    void restoresSnapshotAssignerFromItsCheckpointState() {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var state = assigner.snapshotState(1);
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), state);
        assertEquals(state, restored.snapshotState(2));
    }

    @Test
    void metadataReleaseRequiresCompletedStreamingCheckpointAndRemainsRecoverable() throws Exception {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        TableId table = new TableId("shop", null, "orders");
        assigner.initializeTables(List.of(table), false, true);
        var split = new MySqlSnapshotSplit("orders-0", table, null, null, null);
        var low = new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var high = new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 200L));
        assigner.nextBinlog();
        assigner.captureLow(low);
        assigner.plan(List.of(split));
        assigner.nextSnapshot();
        assigner.finishSnapshot(finished(split, 200));
        assigner.captureHigh(high);
        assertTrue(assigner.onBinlogSplitMetaAssembled(assigner.getBinlogAssignmentGeneration()));

        var handoffCheckpoint = assigner.snapshotState(10);
        assigner.notifyCheckpointComplete(9);
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());
        assigner.notifyCheckpointComplete(10);
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, assigner.phase());

        // Reader's streaming split and Binlog schema history must first be durable.
        var priorToRelease = assigner.snapshotState(11);
        assertFalse(priorToRelease.getSnapshotPendingSplits().isSnapshotMetaReleased());
        assigner.notifyCheckpointComplete(10);
        assertFalse(assigner.isSnapshotMetaReleased());
        assigner.notifyCheckpointComplete(11);
        assertTrue(assigner.isSnapshotMetaReleased());
        var released = assigner.snapshotState(12);
        assertTrue(released.getSnapshotPendingSplits().isSnapshotMetaReleased());
        assertEquals(0, released.getSnapshotPendingSplits().getFinishedSplitInfos().size());

        var serializer = new PendingSplitsStateSerializer();
        var restoredPrior = new MySqlHybridSplitAssigner(
                StartupOptions.initial(),
                (HybridPendingSplitsState) serializer.deserialize(
                        serializer.getVersion(), serializer.serialize(priorToRelease)));
        assertFalse(restoredPrior.isSnapshotMetaReleased());
        var restoredReleased = new MySqlHybridSplitAssigner(
                StartupOptions.initial(),
                (HybridPendingSplitsState) serializer.deserialize(
                        serializer.getVersion(), serializer.serialize(released)));
        assertTrue(restoredReleased.isSnapshotMetaReleased());
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, restoredReleased.phase());
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, handoffCheckpoint.phase());
    }

    private static io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo finished(
            MySqlSnapshotSplit split, long position) {
        return new io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo(split.tableId(),
                split.splitId(), split.lowerInclusive(), split.upperExclusive(),
                new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", position)));
    }
}
