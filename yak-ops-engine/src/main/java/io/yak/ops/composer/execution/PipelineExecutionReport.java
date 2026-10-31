package io.yak.ops.composer.execution;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.composer.definition.TableBinding;
import io.yak.ops.composer.execution.TableExecutionProgress.Checkpoints;
import io.yak.ops.composer.execution.TableExecutionProgress.RealtimeReport;
import io.yak.ops.composer.execution.TableExecutionProgress.RealtimeSummary;
import io.yak.ops.composer.execution.TableExecutionProgress.Report;
import io.yak.ops.composer.execution.TableExecutionProgress.StageResult;
import io.yak.ops.composer.execution.TableExecutionProgress.TableResult;
import io.yak.ops.composer.execution.TableExecutionProgress.TableStages;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.data.TableId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Projects one immutable view of DATA_SYNC preparation state and connector measurements
 * into the existing versioned public report contract.
 *
 * <p>No execution state or lifecycle decisions are owned by this projector.
 */
final class PipelineExecutionReport {
    private final List<TableBinding> plans;
    private final TableMetrics metrics;
    private final boolean continuous;
    private final String dataStep;
    private final List<StageResult> schemaStages;
    private final boolean dataSubmitted;
    private final boolean preparationStopped;

    PipelineExecutionReport(
            List<TableBinding> plans,
            TableMetrics metrics,
            boolean continuous,
            String dataStep,
            List<StageResult> schemaStages,
            boolean dataSubmitted,
            boolean preparationStopped) {
        this.plans = plans;
        this.metrics = metrics;
        this.continuous = continuous;
        this.dataStep = dataStep;
        this.schemaStages = schemaStages;
        this.dataSubmitted = dataSubmitted;
        this.preparationStopped = preparationStopped;
    }

    /** Calculates a split-weighted percent only once every table's denominator is known. */
    public OptionalInt percent() {
        if (!schemaStages.isEmpty() && !dataSubmitted) return OptionalInt.empty();
        if (continuous) return OptionalInt.empty();
        Map<TableId, TableMetrics.Snapshot> snapshot = metrics.snapshot();
        long total = 0;
        long completed = 0;
        for (TableBinding plan : plans) {
            TableMetrics.Snapshot value = snapshot.get(plan.source().table());
            if (value == null || value.plannedSplits() == null) return OptionalInt.empty();
            total += value.plannedSplits();
            completed += Math.min(value.completedSplits(), value.plannedSplits());
        }
        if (total == 0) return OptionalInt.empty();
        return OptionalInt.of((int) Math.min(99, Math.floor(100.0 * completed / total)));
    }

    /** Serializes one immutable report; terminal failure does not erase already committed table work. */
    public String report(JobStatus jobStatus) {
        if (!schemaStages.isEmpty()) return JSONUtils.toJson(preparedReport(jobStatus));
        if (continuous) return JSONUtils.toJson(realtimeReport(jobStatus));
        return JSONUtils.toJson(batchReport(jobStatus));
    }

    private Report batchReport(JobStatus jobStatus) {
        Map<TableId, TableMetrics.Snapshot> snapshot = metrics.snapshot();
        List<TableResult> tables = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            TableBinding plan = plans.get(index);
            TableMetrics.Snapshot value = snapshot.get(plan.source().table());
            if (value == null) value = new TableMetrics.Snapshot(null, 0, 0, 0, null);
            String state = "WAITING";
            boolean sourceComplete = value.plannedSplits() != null && value.completedSplits() >= value.plannedSplits();
            boolean committed = sourceComplete && value.committedRows() == value.readRows();
            if (value.failureCode() != null) state = "FAILED";
            else if (committed || jobStatus == JobStatus.FINISHED) state = "FINISHED";
            else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.CANCELED) state = "CANCELED";
            else if (value.plannedSplits() != null || value.readRows() > 0) state = "RUNNING";
            Integer percent = null;
            if ("FINISHED".equals(state)) percent = 100;
            else if (value.plannedSplits() != null && value.plannedSplits() > 0) {
                percent = (int) Math.min(99, Math.floor(100.0 * value.completedSplits() / value.plannedSplits()));
            }
            tables.add(new TableResult(
                    index,
                    dataStep,
                    state,
                    percent,
                    "SPLITS",
                    value.readRows(),
                    value.committedRows(),
                    value.plannedSplits(),
                    value.completedSplits(),
                    value.failureCode()));
        }
        return new Report(1, Instant.now().toString(), tables);
    }

    /** Returns a compact stage summary without per-table results, or null for bounded reports. */
    public String summary(JobStatus jobStatus) {
        if (!schemaStages.isEmpty())
            return JSONUtils.toJson(preparedReport(jobStatus).summary());
        if (!continuous) return null;
        return JSONUtils.toJson(realtimeSummary(jobStatus, metrics.continuousSnapshot()));
    }

    private RealtimeReport preparedReport(JobStatus status) {
        TableMetrics.ContinuousSnapshot observation = metrics.continuousSnapshot();
        RealtimeReport data = continuous ? realtimeReport(status) : null;
        Report batch = continuous ? null : batchReport(status);
        List<TableStages> tables = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            List<StageResult> stages = new ArrayList<>();
            stages.add(schemaStages.get(index));
            if (!dataSubmitted) {
                stages.add(waitingDataStage(dataStep));
                if (continuous && "FULL".equals(dataStep)) stages.add(waitingDataStage("INCREMENTAL"));
            } else if (continuous) {
                stages.addAll(data.tables().get(index).stages());
            } else {
                TableResult table = batch.tables().get(index);
                stages.add(new StageResult(
                        dataStep,
                        table.status(),
                        table.progressPercent(),
                        "SPLITS",
                        table.readRows(),
                        table.committedRows(),
                        table.plannedSplits(),
                        table.completedSplits(),
                        table.failureCode()));
            }
            tables.add(new TableStages(index, stages));
        }
        List<StageResult> stages = new ArrayList<>();
        stages.add(schemaSummary());
        if (!dataSubmitted) {
            stages.add(waitingDataStage(dataStep));
            if (continuous && "FULL".equals(dataStep)) stages.add(waitingDataStage("INCREMENTAL"));
        } else if (continuous) {
            stages.addAll(data.stages());
        } else {
            TableMetrics.Snapshot totals = aggregate(metrics.snapshot());
            boolean complete = batch.tables().stream().allMatch(table -> "FINISHED".equals(table.status()));
            String state = complete
                    ? "FINISHED"
                    : status == JobStatus.FAILED ? "FAILED" : status == JobStatus.CANCELED ? "CANCELED" : "RUNNING";
            Integer percentage = null;
            if (complete) percentage = 100;
            else if (percent().isPresent()) percentage = percent().getAsInt();
            stages.add(new StageResult(
                    dataStep,
                    state,
                    percentage,
                    "SPLITS",
                    totals.readRows(),
                    totals.committedRows(),
                    totals.plannedSplits(),
                    totals.completedSplits(),
                    totals.failureCode()));
        }
        String observedAt =
                dataSubmitted && continuous ? data.observedAt() : Instant.now().toString();
        String phase = !dataSubmitted ? "SCHEMA" : continuous ? data.phase() : dataStep;
        RealtimeSummary summary = new RealtimeSummary(
                3,
                observedAt,
                continuous ? observation.generation() : 0,
                continuous && observation.restored(),
                continuous ? "GENERATION" : "INSTANCE",
                phase,
                new Checkpoints(
                        continuous ? observation.handoffCheckpointId() : null,
                        continuous ? observation.completedCheckpointId() : null),
                stages);
        return new RealtimeReport(
                3,
                observedAt,
                summary.generation(),
                summary.restored(),
                summary.counterScope(),
                phase,
                summary.checkpoints(),
                stages,
                tables,
                summary);
    }

    private StageResult waitingDataStage(String step) {
        return new StageResult(
                step, preparationStopped ? "CANCELED" : "WAITING", null, "SPLITS", null, null, null, null, null);
    }

    private StageResult schemaSummary() {
        int completed = (int) schemaStages.stream()
                .filter(stage -> "FINISHED".equals(stage.status()))
                .count();
        String failure = schemaStages.stream()
                .map(StageResult::failureCode)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        boolean unknown = schemaStages.stream().anyMatch(stage -> "UNKNOWN".equals(stage.status()));
        String status = unknown
                ? "UNKNOWN"
                : completed == schemaStages.size()
                        ? "FINISHED"
                        : failure != null
                                ? "FAILED"
                                : preparationStopped
                                        ? "CANCELED"
                                        : schemaStages.stream().anyMatch(stage -> "RUNNING".equals(stage.status()))
                                                        || completed > 0
                                                ? "RUNNING"
                                                : "WAITING";
        return schemaStage(status, schemaStages.size(), completed, null, failure);
    }

    static StageResult schemaStage(String status, int planned, int completed, String action, String failure) {
        return new StageResult(
                "SCHEMA",
                status,
                (int) (100L * completed / planned),
                "TABLES",
                null,
                null,
                null,
                null,
                failure,
                planned,
                completed,
                action);
    }

    private RealtimeReport realtimeReport(JobStatus status) {
        TableMetrics.ContinuousSnapshot observation = metrics.continuousSnapshot();
        List<TableStages> tables = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            TableId id = plans.get(index).source().table();
            TableMetrics.Snapshot full = observation.snapshotTables().get(id);
            TableMetrics.Snapshot incremental = observation.incrementalTables().get(id);
            tables.add(new TableStages(
                    index,
                    "FULL".equals(dataStep)
                            ? List.of(
                                    stage("FULL", full, observation, status),
                                    stage("INCREMENTAL", incremental, observation, status))
                            : List.of(stage("INCREMENTAL", incremental, observation, status))));
        }
        RealtimeSummary summary = realtimeSummary(status, observation);
        return new RealtimeReport(
                2,
                summary.observedAt(),
                summary.generation(),
                summary.restored(),
                summary.counterScope(),
                summary.phase(),
                summary.checkpoints(),
                summary.stages(),
                tables,
                summary);
    }

    private RealtimeSummary realtimeSummary(JobStatus status, TableMetrics.ContinuousSnapshot observation) {
        List<StageResult> stages = "FULL".equals(dataStep)
                ? List.of(
                        stage("FULL", aggregate(observation.snapshotTables()), observation, status),
                        stage("INCREMENTAL", aggregate(observation.incrementalTables()), observation, status))
                : List.of(stage("INCREMENTAL", aggregate(observation.incrementalTables()), observation, status));
        return new RealtimeSummary(
                2,
                Instant.ofEpochMilli(observation.observedAt()).toString(),
                observation.generation(),
                observation.restored(),
                "GENERATION",
                observation.phase().name(),
                new Checkpoints(observation.handoffCheckpointId(), observation.completedCheckpointId()),
                stages);
    }

    private TableMetrics.Snapshot aggregate(Map<TableId, TableMetrics.Snapshot> values) {
        Integer planned = 0;
        long completed = 0;
        long read = 0;
        long committed = 0;
        String failure = null;
        for (TableBinding plan : plans) {
            TableMetrics.Snapshot value = values.get(plan.source().table());
            if (value == null) {
                planned = null;
                continue;
            }
            if (planned != null && value.plannedSplits() != null) planned += value.plannedSplits();
            else planned = null;
            completed += value.completedSplits();
            read += value.readRows();
            committed += value.committedRows();
            if (value.failureCode() != null) failure = value.failureCode();
        }
        return new TableMetrics.Snapshot(planned, completed, read, committed, failure);
    }

    private StageResult stage(
            String step,
            TableMetrics.Snapshot value,
            TableMetrics.ContinuousSnapshot observation,
            JobStatus jobStatus) {
        if (value == null) value = new TableMetrics.Snapshot(null, 0, 0, 0, null);
        boolean full = "FULL".equals(step);
        String state;
        if (full && observation.fullComplete()) state = "FINISHED";
        else if (value.failureCode() != null) state = "FAILED";
        else if (jobStatus == JobStatus.FAILED) state = "FAILED";
        else if (jobStatus == JobStatus.CANCELED || jobStatus == JobStatus.CANCELLING) state = "CANCELED";
        else if (jobStatus == JobStatus.FAILING || (observation.generation() > 0 && !observation.active()))
            state = "UNKNOWN";
        else if (observation.generation() == 0 || (!full && observation.phase() != TableMetrics.Phase.STREAMING))
            state = "WAITING";
        else state = "RUNNING";
        // A queued incremental phase was not executed when initialization failed or was cancelled.
        if (!full && "FULL".equals(dataStep) && !observation.fullComplete() && jobStatus == JobStatus.FAILED)
            state = "CANCELED";
        Integer percent = null;
        if (full && observation.fullComplete()) percent = 100;
        else if (full && !observation.restored() && value.plannedSplits() != null && value.plannedSplits() > 0) {
            percent = (int) Math.min(99, Math.floor(100.0 * value.completedSplits() / value.plannedSplits()));
        }
        Long read = value.readRows();
        Long committed = value.committedRows();
        Integer planned = value.plannedSplits();
        Long completed = value.completedSplits();
        if (full && observation.restored()) {
            // Checkpoints persist execution state, not historical measurement totals.
            read = null;
            committed = null;
            planned = null;
            completed = null;
        }
        if (!full) {
            planned = null;
            completed = null;
        }
        return new StageResult(
                step,
                state,
                percent,
                full ? "SPLITS" : "RECORDS",
                read,
                committed,
                planned,
                completed,
                value.failureCode());
    }
}
