package io.yak.ops.plugin.task.datasync.report;

import io.yak.ops.extract.master.transportor.DataSyncExecutionSummary;
import io.yak.ops.plugin.task.datasync.DataSyncTableMapping;
import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionState;
import io.yak.ops.plugin.task.datasync.execution.DataSyncRecoveryPointSnapshot;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncExecutionSnapshot;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncTableMetrics;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncTableSplitMetrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converts Flink CDC coordinator and table metrics into the stable three-stage report contract.
 *
 * <p>The phase is monotonic for one execution: schema preparation, snapshot, handoff, streaming.
 */
public final class DataSyncExecutionSummaryTracker {

    private static final String INITIALIZING = "INITIALIZING";
    private static final String SNAPSHOT = "SNAPSHOT";
    private static final String HANDOFF = "HANDOFF";
    private static final String STREAMING = "STREAMING";

    private final List<DataSyncTableMapping> mappings;
    private final boolean restored;
    private final Map<Integer, Long> fullCommittedRows = new HashMap<>();

    private String phase = INITIALIZING;
    private boolean fullSnapshotFrozen;

    public DataSyncExecutionSummaryTracker(List<DataSyncTableMapping> mappings, boolean restored) {
        this.mappings = mappings == null ? Collections.emptyList() : new ArrayList<>(mappings);
        this.restored = restored;
    }

    public DataSyncExecutionSummary update(
            DataSyncExecutionSnapshot snapshot,
            DataSyncExecutionState executionState,
            DataSyncRecoveryPointSnapshot recoveryPoint) {
        DataSyncExecutionSnapshot observed = snapshot == null
                ? DataSyncExecutionSnapshot.builder().build()
                : snapshot;

        List<TableObservation> observations = new ArrayList<>();
        int completedSchemaTables = 0;
        for (int index = 0; index < mappings.size(); index++) {
            DataSyncTableMapping mapping = mappings.get(index);
            DataSyncTableMetrics tableMetrics = aggregateTableMetrics(mapping, observed.getTables());
            boolean schemaPrepared = executionState == DataSyncExecutionState.FINISHED
                    || isSchemaPrepared(mapping, observed);
            if (schemaPrepared) {
                completedSchemaTables++;
            }
            observations.add(new TableObservation(
                    index,
                    mapping,
                    schemaPrepared,
                    tableMetrics,
                    aggregateSplitMetrics(mapping, observed.getTableSplits())));
        }

        boolean schemaComplete = !mappings.isEmpty() && completedSchemaTables == mappings.size();
        String previousPhase = phase;
        advancePhase(observed, schemaComplete, executionState);
        boolean fullComplete = phaseRank(phase) >= phaseRank(HANDOFF)
                || executionState == DataSyncExecutionState.FINISHED;
        if (fullComplete && !fullSnapshotFrozen) {
            if (phaseRank(previousPhase) >= phaseRank(SNAPSHOT)) {
                for (TableObservation observation : observations) {
                    Long committed = observation.metrics == null
                            ? null
                            : observation.metrics.getCommittedRecords();
                    if (committed != null) {
                        fullCommittedRows.put(observation.mappingIndex, committed);
                    }
                }
            }
            // If the first sample already observes streaming, the full/incremental commit boundary
            // is unknown. Keep incremental committedRows null rather than inventing a baseline.
            fullSnapshotFrozen = true;
        }

        String schemaStatus = schemaComplete
                ? "FINISHED"
                : terminalStatus(executionState, "RUNNING");
        String fullStatus;
        if (!schemaComplete) {
            fullStatus = "WAITING";
        } else if (fullComplete) {
            fullStatus = "FINISHED";
        } else if (executionState == DataSyncExecutionState.FAILED
                || executionState == DataSyncExecutionState.CANCELED) {
            fullStatus = terminalStatus(executionState, "WAITING");
        } else {
            fullStatus = SNAPSHOT.equals(phase) ? "RUNNING" : "WAITING";
        }

        String incrementalStatus;
        if (!fullComplete) {
            incrementalStatus = "WAITING";
        } else if (executionState == DataSyncExecutionState.FINISHED) {
            incrementalStatus = "FINISHED";
        } else if (executionState == DataSyncExecutionState.FAILED
                || executionState == DataSyncExecutionState.CANCELED) {
            incrementalStatus = terminalStatus(executionState, "WAITING");
        } else {
            incrementalStatus = "RUNNING";
        }

        List<DataSyncExecutionSummary.TableStages> tableStages = new ArrayList<>();
        List<DataSyncExecutionSummary.StageResult> fullTableStages = new ArrayList<>();
        List<DataSyncExecutionSummary.StageResult> incrementalTableStages = new ArrayList<>();
        for (TableObservation observation : observations) {
            DataSyncExecutionSummary.StageResult schema = schemaStage(
                    observation.schemaPrepared,
                    terminalStatus(executionState, "RUNNING"));
            DataSyncExecutionSummary.StageResult full = fullTableStage(
                    observation, schemaComplete, fullStatus);
            DataSyncExecutionSummary.StageResult incremental = incrementalTableStage(
                    observation, fullComplete, incrementalStatus);
            tableStages.add(DataSyncExecutionSummary.TableStages.builder()
                    .mappingIndex(observation.mappingIndex)
                    .stages(List.of(schema, full, incremental))
                    .build());
            fullTableStages.add(full);
            incrementalTableStages.add(incremental);
        }

        Integer schemaProgress = mappings.isEmpty()
                ? 0
                : (int) Math.floor((100.0d * completedSchemaTables) / mappings.size());
        DataSyncExecutionSummary.StageResult schema = DataSyncExecutionSummary.StageResult.builder()
                .step("SCHEMA")
                .status(schemaStatus)
                .progressPercent(schemaProgress)
                .progressUnit("TABLES")
                .plannedTables(mappings.size())
                .completedTables(completedSchemaTables)
                .failureCode(schemaStatus.equals("FAILED") ? "SCHEMA_PREPARATION_FAILED" : null)
                .build();

        DataSyncExecutionSummary.StageResult full = aggregateFullStage(
                fullTableStages, fullStatus);
        DataSyncExecutionSummary.StageResult incremental = aggregateIncrementalStage(
                incrementalTableStages, incrementalStatus);

        Long completedCheckpoint = checkpointId(recoveryPoint);
        Long handoffCheckpoint = phaseRank(phase) >= phaseRank(HANDOFF)
                ? completedCheckpoint
                : null;
        long observedAt = observed.getCollectedAt() == null
                ? System.currentTimeMillis()
                : observed.getCollectedAt();

        return DataSyncExecutionSummary.builder()
                .version(3)
                .observedAt(Instant.ofEpochMilli(observedAt).toString())
                .generation(restored ? 1 : 0)
                .restored(restored)
                .counterScope("GENERATION")
                .phase(phase)
                .checkpoints(DataSyncExecutionSummary.Checkpoints.builder()
                        .handoffId(handoffCheckpoint)
                        .completedId(completedCheckpoint)
                        .build())
                .stages(List.of(schema, full, incremental))
                .tables(tableStages)
                .build();
    }

    private void advancePhase(
            DataSyncExecutionSnapshot snapshot,
            boolean schemaComplete,
            DataSyncExecutionState executionState) {
        String candidate = INITIALIZING;
        if (executionState == DataSyncExecutionState.FINISHED
                || Boolean.TRUE.equals(snapshot.getStreamReading())) {
            candidate = STREAMING;
        } else if (schemaComplete && Boolean.TRUE.equals(snapshot.getSnapshotting())) {
            candidate = SNAPSHOT;
        } else if (schemaComplete
                && "SNAPSHOT".equals(phase)
                && Boolean.FALSE.equals(snapshot.getSnapshotting())) {
            candidate = HANDOFF;
        } else if (schemaComplete
                && (executionState == DataSyncExecutionState.FAILED
                        || executionState == DataSyncExecutionState.CANCELED)
                && INITIALIZING.equals(phase)) {
            candidate = SNAPSHOT;
        } else if (schemaComplete
                && snapshot.getSnapshotting() == null
                && snapshot.getSnapshotRecords() != null
                && snapshot.getSnapshotRecords() > 0L) {
            candidate = SNAPSHOT;
        }
        if (phaseRank(candidate) > phaseRank(phase)) {
            phase = candidate;
        }
    }

    private boolean isSchemaPrepared(
            DataSyncTableMapping mapping,
            DataSyncExecutionSnapshot snapshot) {
        if (Boolean.TRUE.equals(snapshot.getStreamReading())) {
            return true;
        }

        List<String> preparedTables = snapshot.getSchemaPreparedTables() == null
                ? Collections.emptyList()
                : snapshot.getSchemaPreparedTables();
        if (mapping.isDatabaseScope()) {
            long preparedForDatabase = preparedTables.stream()
                    .filter(mapping::matchesSinkTable)
                    .count();
            Integer sourceTablesTotal = snapshot.getSourceTablesTotal();
            if (sourceTablesTotal != null
                    && sourceTablesTotal > 0
                    && preparedForDatabase >= sourceTablesTotal) {
                return true;
            }
        } else if (preparedTables.stream().anyMatch(mapping::matchesSinkTable)) {
            return true;
        }

        int expected = expectedSchemaTableCount(snapshot);
        Integer preparedCount = snapshot.getSchemaTablesPrepared();
        if (preparedCount != null && expected > 0 && preparedCount >= expected) {
            return true;
        }

        return false;
    }

    private int expectedSchemaTableCount(DataSyncExecutionSnapshot snapshot) {
        int explicitTables = (int) mappings.stream()
                .filter(mapping -> mapping != null && !mapping.isDatabaseScope())
                .map(DataSyncTableMapping::getSinkTable)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        int databaseTables = 0;
        for (DataSyncTableMapping mapping : mappings) {
            if (mapping != null && mapping.isDatabaseScope()) {
                databaseTables += snapshot.getSourceTablesTotal() == null
                        ? 0
                        : snapshot.getSourceTablesTotal();
            }
        }
        return explicitTables + databaseTables;
    }

    private static DataSyncTableMetrics aggregateTableMetrics(
            DataSyncTableMapping mapping, List<DataSyncTableMetrics> tables) {
        if (mapping == null || tables == null || tables.isEmpty()) {
            return null;
        }
        List<DataSyncTableMetrics> matched = tables.stream()
                .filter(table -> table != null
                        && (mapping.matchesSourceTable(table.getSourceTable())
                                || mapping.matchesSinkTable(table.getSinkTable())))
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return null;
        }
        return DataSyncTableMetrics.builder()
                .sourceTable(mapping.getSourceTable())
                .sinkTable(mapping.getSinkTable())
                .snapshotRecords(sumTableMetrics(matched, DataSyncTableMetrics::getSnapshotRecords))
                .incrementalRecords(sumTableMetrics(matched, DataSyncTableMetrics::getIncrementalRecords))
                .committedRecords(sumTableMetrics(matched, DataSyncTableMetrics::getCommittedRecords))
                .readRecords(sumTableMetrics(matched, DataSyncTableMetrics::getReadRecords))
                .build();
    }

    private static SplitProgress aggregateSplitMetrics(
            DataSyncTableMapping mapping, List<DataSyncTableSplitMetrics> tableSplits) {
        if (mapping == null || tableSplits == null || tableSplits.isEmpty()) {
            return new SplitProgress(null, null);
        }
        List<DataSyncTableSplitMetrics> matched = tableSplits.stream()
                .filter(metric -> metric != null && mapping.matchesSourceTable(metric.getTableIdentifier()))
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return new SplitProgress(null, null);
        }
        return new SplitProgress(
                sumSplitMetrics(matched, DataSyncTableSplitMetrics::getPlannedSplits),
                sumSplitMetrics(matched, DataSyncTableSplitMetrics::getCompletedSplits));
    }

    private DataSyncExecutionSummary.StageResult schemaStage(
            boolean prepared, String terminalStatus) {
        String status = prepared
                ? "FINISHED"
                : ("FAILED".equals(terminalStatus) || "CANCELED".equals(terminalStatus)
                        ? terminalStatus
                        : "RUNNING");
        return DataSyncExecutionSummary.StageResult.builder()
                .step("SCHEMA")
                .status(status)
                .progressPercent(prepared ? 100 : 0)
                .progressUnit("TABLES")
                .plannedTables(1)
                .completedTables(prepared ? 1 : 0)
                .failureCode("FAILED".equals(status) ? "SCHEMA_PREPARATION_FAILED" : null)
                .action(prepared ? "PREPARED" : null)
                .build();
    }

    private DataSyncExecutionSummary.StageResult fullTableStage(
            TableObservation observation, boolean schemaComplete, String globalStatus) {
        String status = !schemaComplete
                ? "WAITING"
                : globalStatus;
        Long committed = fullCommittedRows.get(observation.mappingIndex);
        if (!fullSnapshotFrozen && committed == null && observation.metrics != null) {
            committed = observation.metrics.getCommittedRecords();
        }
        Integer progress = progressPercent(
                status,
                observation.splits.planned,
                observation.splits.completed);
        return DataSyncExecutionSummary.StageResult.builder()
                .step("FULL")
                .status(status)
                .progressPercent(progress)
                .progressUnit("SPLITS")
                .readRows(observation.metrics == null ? null : observation.metrics.getSnapshotRecords())
                .committedRows(committed)
                .plannedSplits(observation.splits.planned)
                .completedSplits(observation.splits.completed)
                .build();
    }

    private DataSyncExecutionSummary.StageResult incrementalTableStage(
            TableObservation observation, boolean fullComplete, String globalStatus) {
        String status = fullComplete ? globalStatus : "WAITING";
        Long incrementalCommitted = null;
        Long currentCommitted = observation.metrics == null
                ? null
                : observation.metrics.getCommittedRecords();
        Long snapshotCommitted = fullCommittedRows.get(observation.mappingIndex);
        if (currentCommitted != null && snapshotCommitted != null) {
            incrementalCommitted = Math.max(0L, currentCommitted - snapshotCommitted);
        }
        return DataSyncExecutionSummary.StageResult.builder()
                .step("INCREMENTAL")
                .status(status)
                .progressPercent("FINISHED".equals(status) ? 100 : null)
                .progressUnit("RECORDS")
                .readRows(observation.metrics == null ? null : observation.metrics.getIncrementalRecords())
                .committedRows(incrementalCommitted)
                .build();
    }

    private static DataSyncExecutionSummary.StageResult aggregateFullStage(
            List<DataSyncExecutionSummary.StageResult> tables, String status) {
        Long planned = sumStageValuesIfAll(tables, DataSyncExecutionSummary.StageResult::getPlannedSplits);
        Long completed = sumStageValuesIfAll(tables, DataSyncExecutionSummary.StageResult::getCompletedSplits);
        Long read = sumStageValues(tables, DataSyncExecutionSummary.StageResult::getReadRows);
        Long committed = sumStageValues(tables, DataSyncExecutionSummary.StageResult::getCommittedRows);
        return DataSyncExecutionSummary.StageResult.builder()
                .step("FULL")
                .status(status)
                .progressPercent(progressPercent(status, planned, completed))
                .progressUnit("SPLITS")
                .readRows(read)
                .committedRows(committed)
                .plannedSplits(planned)
                .completedSplits(completed)
                .build();
    }

    private static DataSyncExecutionSummary.StageResult aggregateIncrementalStage(
            List<DataSyncExecutionSummary.StageResult> tables, String status) {
        return DataSyncExecutionSummary.StageResult.builder()
                .step("INCREMENTAL")
                .status(status)
                .progressPercent("FINISHED".equals(status) ? 100 : null)
                .progressUnit("RECORDS")
                .readRows(sumStageValues(tables, DataSyncExecutionSummary.StageResult::getReadRows))
                .committedRows(sumStageValues(tables, DataSyncExecutionSummary.StageResult::getCommittedRows))
                .build();
    }

    private static Integer progressPercent(String status, Long planned, Long completed) {
        if ("FINISHED".equals(status)) {
            return 100;
        }
        if (planned == null || completed == null || planned <= 0L) {
            return null;
        }
        return (int) Math.min(99L, Math.max(0L, (completed * 100L) / planned));
    }

    private static Long checkpointId(DataSyncRecoveryPointSnapshot recoveryPoint) {
        if (recoveryPoint == null || recoveryPoint.getCheckpointId() == null) {
            return null;
        }
        try {
            return Long.valueOf(recoveryPoint.getCheckpointId());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String terminalStatus(DataSyncExecutionState state, String fallback) {
        if (state == DataSyncExecutionState.FAILED) {
            return "FAILED";
        }
        if (state == DataSyncExecutionState.CANCELED) {
            return "CANCELED";
        }
        return fallback;
    }

    private static int phaseRank(String value) {
        if (STREAMING.equals(value)) {
            return 3;
        }
        if (HANDOFF.equals(value)) {
            return 2;
        }
        if (SNAPSHOT.equals(value)) {
            return 1;
        }
        return 0;
    }

    private static Long sumTableMetrics(
            List<DataSyncTableMetrics> values,
            Function<DataSyncTableMetrics, Long> getter) {
        boolean found = false;
        long total = 0L;
        for (DataSyncTableMetrics value : values) {
            Long item = getter.apply(value);
            if (item != null) {
                found = true;
                total += item;
            }
        }
        return found ? total : null;
    }

    private static Long sumSplitMetrics(
            List<DataSyncTableSplitMetrics> values,
            Function<DataSyncTableSplitMetrics, Long> getter) {
        boolean found = false;
        long total = 0L;
        for (DataSyncTableSplitMetrics value : values) {
            Long item = getter.apply(value);
            if (item != null) {
                found = true;
                total += item;
            }
        }
        return found ? total : null;
    }

    private static Long sumStageValuesIfAll(
            List<DataSyncExecutionSummary.StageResult> values,
            Function<DataSyncExecutionSummary.StageResult, Long> getter) {
        if (values.isEmpty()) {
            return null;
        }
        long total = 0L;
        for (DataSyncExecutionSummary.StageResult value : values) {
            Long item = getter.apply(value);
            if (item == null) {
                return null;
            }
            total += item;
        }
        return total;
    }

    private static Long sumStageValues(
            List<DataSyncExecutionSummary.StageResult> values,
            Function<DataSyncExecutionSummary.StageResult, Long> getter) {
        boolean found = false;
        long total = 0L;
        for (DataSyncExecutionSummary.StageResult value : values) {
            Long item = getter.apply(value);
            if (item != null) {
                found = true;
                total += item;
            }
        }
        return found ? total : null;
    }

    private static final class TableObservation {

        private final int mappingIndex;
        private final DataSyncTableMapping mapping;
        private final boolean schemaPrepared;
        private final DataSyncTableMetrics metrics;
        private final SplitProgress splits;

        private TableObservation(
                int mappingIndex,
                DataSyncTableMapping mapping,
                boolean schemaPrepared,
                DataSyncTableMetrics metrics,
                SplitProgress splits) {
            this.mappingIndex = mappingIndex;
            this.mapping = mapping;
            this.schemaPrepared = schemaPrepared;
            this.metrics = metrics;
            this.splits = splits;
        }
    }

    private static final class SplitProgress {

        private final Long planned;
        private final Long completed;

        private SplitProgress(Long planned, Long completed) {
            this.planned = planned;
            this.completed = completed;
        }
    }
}
