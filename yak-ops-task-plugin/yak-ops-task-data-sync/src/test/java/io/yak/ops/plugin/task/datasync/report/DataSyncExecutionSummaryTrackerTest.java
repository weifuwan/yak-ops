package io.yak.ops.plugin.task.datasync.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.extract.master.transportor.DataSyncExecutionSummary;
import io.yak.ops.plugin.task.datasync.DataSyncTableMapping;
import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionState;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncExecutionSnapshot;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncTableMetrics;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncTableSplitMetrics;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class DataSyncExecutionSummaryTrackerTest {

    @Test
    void advancesSchemaSnapshotHandoffAndStreamingStages() {
        DataSyncTableMapping mapping = DataSyncTableMapping.builder()
                .sourceTable("source_db.orders")
                .sinkTable("target_db.orders_target")
                .build();
        DataSyncExecutionSummaryTracker tracker =
                new DataSyncExecutionSummaryTracker(Collections.singletonList(mapping), false);

        DataSyncExecutionSummary initializing = tracker.update(
                snapshot(true, false, 0, Collections.emptyList(), 0L, 0L, 0L, 10L, 0L),
                DataSyncExecutionState.RUNNING,
                null);
        assertEquals("INITIALIZING", initializing.getPhase());
        assertEquals("RUNNING", initializing.getStages().get(0).getStatus());
        assertEquals("WAITING", initializing.getStages().get(1).getStatus());
        assertEquals("WAITING", initializing.getStages().get(2).getStatus());

        DataSyncExecutionSummary snapshotting = tracker.update(
                snapshot(true, false, 1, List.of("target_db.orders_target"),
                        12L, 0L, 12L, 10L, 4L),
                DataSyncExecutionState.RUNNING,
                null);
        assertEquals("SNAPSHOT", snapshotting.getPhase());
        assertEquals("FINISHED", snapshotting.getStages().get(0).getStatus());
        assertEquals("RUNNING", snapshotting.getStages().get(1).getStatus());
        assertEquals(40, snapshotting.getStages().get(1).getProgressPercent());
        assertEquals("WAITING", snapshotting.getStages().get(2).getStatus());

        DataSyncExecutionSummary handoff = tracker.update(
                snapshot(false, false, 1, List.of("target_db.orders_target"),
                        12L, 0L, 12L, 10L, 10L),
                DataSyncExecutionState.RUNNING,
                null);
        assertEquals("HANDOFF", handoff.getPhase());
        assertEquals("FINISHED", handoff.getStages().get(1).getStatus());
        assertEquals("RUNNING", handoff.getStages().get(2).getStatus());
        assertEquals(12L, handoff.getStages().get(1).getCommittedRows());

        DataSyncExecutionSummary streaming = tracker.update(
                snapshot(false, true, 1, List.of("target_db.orders_target"),
                        12L, 3L, 15L, 10L, 10L),
                DataSyncExecutionState.RUNNING,
                null);
        assertEquals("STREAMING", streaming.getPhase());
        assertEquals("RUNNING", streaming.getStages().get(2).getStatus());
        assertEquals(3L, streaming.getStages().get(2).getReadRows());
        assertEquals(3L, streaming.getStages().get(2).getCommittedRows());

        DataSyncExecutionSummary finished = tracker.update(
                snapshot(false, true, 1, List.of("target_db.orders_target"),
                        12L, 3L, 15L, 10L, 10L),
                DataSyncExecutionState.FINISHED,
                null);
        assertEquals("FINISHED", finished.getStages().get(0).getStatus());
        assertEquals("FINISHED", finished.getStages().get(1).getStatus());
        assertEquals("FINISHED", finished.getStages().get(2).getStatus());
        assertEquals(100, finished.getStages().get(2).getProgressPercent());
    }

    private static DataSyncExecutionSnapshot snapshot(
            boolean snapshotting,
            boolean streamReading,
            int preparedTables,
            List<String> preparedTableNames,
            long snapshotRows,
            long incrementalRows,
            long committedRows,
            long plannedSplits,
            long completedSplits) {
        return DataSyncExecutionSnapshot.builder()
                .collectedAt(System.currentTimeMillis())
                .metricsState("AVAILABLE")
                .snapshotting(snapshotting)
                .streamReading(streamReading)
                .sourceTablesTotal(1)
                .schemaTablesPrepared(preparedTables)
                .schemaPreparedTables(preparedTableNames)
                .tableSplits(Collections.singletonList(DataSyncTableSplitMetrics.builder()
                        .tableIdentifier("source_db.orders")
                        .plannedSplits(plannedSplits)
                        .completedSplits(completedSplits)
                        .build()))
                .tables(Collections.singletonList(DataSyncTableMetrics.builder()
                        .sourceTable("source_db.orders")
                        .sinkTable("target_db.orders_target")
                        .snapshotRecords(snapshotRows)
                        .incrementalRecords(incrementalRows)
                        .committedRecords(committedRows)
                        .readRecords(snapshotRows + incrementalRows)
                        .build()))
                .build();
    }
}
