package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.task.datasync.plan.TablePlan;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Projects measured bounded-table work into a non-secret, versioned execution report. */
public final class TableExecutionProgress {
    private final List<TablePlan> plans;
    private final TableMetrics metrics;

    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics) {
        this.plans = List.copyOf(plans);
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
    }

    /** Calculates a split-weighted percent only once every table's denominator is known. */
    public OptionalInt percent() {
        Map<TableId, TableMetrics.Snapshot> snapshot = metrics.snapshot();
        long total = 0;
        long completed = 0;
        for (var plan : plans) {
            var value = snapshot.get(plan.source().table());
            if (value == null || value.plannedSplits() == null) return OptionalInt.empty();
            total += value.plannedSplits();
            completed += Math.min(value.completedSplits(), value.plannedSplits());
        }
        if (total == 0) return OptionalInt.empty();
        return OptionalInt.of((int) Math.min(99, Math.floor(100.0 * completed / total)));
    }

    /** Serializes one immutable report; terminal failure does not erase already committed table work. */
    public String report(JobStatus jobStatus) {
        Map<TableId, TableMetrics.Snapshot> snapshot = metrics.snapshot();
        List<TableResult> tables = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            var plan = plans.get(index);
            var value = snapshot.get(plan.source().table());
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
                    "FULL",
                    state,
                    percent,
                    "SPLITS",
                    value.readRows(),
                    value.committedRows(),
                    value.plannedSplits(),
                    value.completedSplits(),
                    value.failureCode()));
        }
        return JSONUtils.toJson(new Report(1, Instant.now().toString(), tables));
    }

    /** Report fields contain observations only; the matching table identities come from the pinned definition. */
    public record Report(int version, String observedAt, List<TableResult> tables) {
        public Report {
            tables = List.copyOf(tables);
        }
    }

    /** One mapping-indexed FULL-stage observation. Rows mean emitted and acknowledged input records, not affected rows. */
    public record TableResult(
            int mappingIndex,
            String step,
            String status,
            Integer progressPercent,
            String progressUnit,
            long readRows,
            long committedRows,
            Integer plannedSplits,
            long completedSplits,
            String failureCode) {}
}
