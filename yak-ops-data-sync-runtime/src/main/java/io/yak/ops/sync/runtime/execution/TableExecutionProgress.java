package io.yak.ops.sync.runtime.execution;

import static io.yak.ops.sync.runtime.execution.PipelineExecutionReport.schemaStage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.sync.runtime.plan.TablePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Owns the active per-table preparation state and Connector metrics for one Task Instance. */
public final class TableExecutionProgress {
    private final List<TablePlan> plans;
    private final TableMetrics metrics;
    private final boolean continuous;
    private final String dataStep;
    private final List<StageResult> schemaStages;
    private boolean dataSubmitted;
    private boolean preparationStopped;

    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics) {
        this(plans, metrics, false);
    }

    /** Creates observations for a bounded batch or a snapshot-plus-continuous source. */
    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics, boolean continuous) {
        this(plans, metrics, continuous, "FULL");
    }

    /** Selects the physically executed data step rather than inferring it from the Definition. */
    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics, boolean continuous, String dataStep) {
        this.dataStep = java.util.Objects.requireNonNull(dataStep, "dataStep");
        this.continuous = continuous;
        this.plans = List.copyOf(plans);
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
        this.schemaStages = new ArrayList<>();
        if (plans.stream().anyMatch(plan -> plan.schemaChange() != null)) {
            if (plans.stream().anyMatch(plan -> plan.schemaChange() == null)) {
                throw new IllegalArgumentException("Schema preparation must cover every table mapping");
            }
            for (int index = 0; index < plans.size(); index++) {
                schemaStages.add(schemaStage("WAITING", 1, 0, null, null));
            }
        }
    }

    /** Returns the shared measurements used by the compiled row pipeline. */
    public TableMetrics metrics() {
        return metrics;
    }

    /** Marks a table's preparation as active without claiming that DDL has completed. */
    public synchronized void schemaStarted(int index) {
        schemaStages.set(index, schemaStage("RUNNING", 1, 0, null, null));
    }

    /** Records complete target readback, preserving this fact across row-engine generations. */
    public synchronized void schemaFinished(int index, String action) {
        schemaStages.set(index, schemaStage("FINISHED", 1, 1, action, null));
    }

    /** Records a safe failure identifier for a table whose preparation did not verify. */
    public synchronized void schemaFailed(int index, String failureCode) {
        schemaStages.set(index, schemaStage("FAILED", 1, 0, null, failureCode));
    }

    /** Records an unconfirmed external DDL outcome without claiming a terminal table result. */
    public synchronized void schemaUnknown(int index) {
        preparationStopped = true;
        schemaStages.set(index, schemaStage("UNKNOWN", 1, 0, null, null));
    }

    /** Records cancellation after the current preparation operation has returned. */
    public synchronized void schemaCanceled(int index) {
        schemaStages.set(index, schemaStage("CANCELED", 1, 0, null, null));
    }

    /** Stops unexecuted preparations without erasing previously verified tables. */
    public synchronized void preparationEnded() {
        preparationStopped = true;
        for (int index = 0; index < schemaStages.size(); index++) {
            StageResult stage = schemaStages.get(index);
            if ("WAITING".equals(stage.status()) || "RUNNING".equals(stage.status())) {
                schemaStages.set(index, schemaStage("CANCELED", 1, 0, null, null));
            }
        }
    }

    /** Marks the single engine-submission boundary after all target structures have verified. */
    public synchronized void dataSubmissionStarted() {
        if (schemaStages.stream().anyMatch(stage -> !"FINISHED".equals(stage.status()))) {
            throw new IllegalStateException("Every target must verify before row-engine submission");
        }
        dataSubmitted = true;
    }

    /** Returns progress from a point-in-time observation without mutating execution state. */
    public synchronized OptionalInt percent() {
        return reporter().percent();
    }

    /** Returns the existing versioned public JSON report contract. */
    public synchronized String report(JobStatus jobStatus) {
        return reporter().report(jobStatus);
    }

    /** Returns the compact public report summary, when available. */
    public synchronized String summary(JobStatus jobStatus) {
        return reporter().summary(jobStatus);
    }

    private PipelineExecutionReport reporter() {
        return new PipelineExecutionReport(
                plans, metrics, continuous, dataStep, List.copyOf(schemaStages), dataSubmitted, preparationStopped);
    }
    /** Two completed-checkpoint observations; unavailable restored identifiers remain null. */
    public static final class Checkpoints {
        private final Long handoffId;
        private final Long completedId;

        @JsonCreator
        public Checkpoints(@JsonProperty("handoffId") Long handoffId, @JsonProperty("completedId") Long completedId) {
            this.handoffId = handoffId;
            this.completedId = completedId;
        }

        @JsonProperty("handoffId")
        public Long handoffId() {
            return handoffId;
        }

        @JsonProperty("completedId")
        public Long completedId() {
            return completedId;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Checkpoints other)) return false;
            return java.util.Objects.equals(handoffId, other.handoffId)
                    && java.util.Objects.equals(completedId, other.completedId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(handoffId, completedId);
        }
    }

    /** Public phase measurements. Incremental percent is always null; counters count input records. */
    public static final class StageResult {
        private final String step;
        private final String status;
        private final Integer progressPercent;
        private final String progressUnit;
        private final Long readRows;
        private final Long committedRows;
        private final Integer plannedSplits;
        private final Long completedSplits;
        private final String failureCode;
        private final Integer plannedTables;
        private final Integer completedTables;
        private final String action;

        @JsonCreator
        public StageResult(
                @JsonProperty("step") String step,
                @JsonProperty("status") String status,
                @JsonProperty("progressPercent") Integer progressPercent,
                @JsonProperty("progressUnit") String progressUnit,
                @JsonProperty("readRows") Long readRows,
                @JsonProperty("committedRows") Long committedRows,
                @JsonProperty("plannedSplits") Integer plannedSplits,
                @JsonProperty("completedSplits") Long completedSplits,
                @JsonProperty("failureCode") String failureCode,
                @JsonProperty("plannedTables") Integer plannedTables,
                @JsonProperty("completedTables") Integer completedTables,
                @JsonProperty("action") String action) {
            this.step = step;
            this.status = status;
            this.progressPercent = progressPercent;
            this.progressUnit = progressUnit;
            this.readRows = readRows;
            this.committedRows = committedRows;
            this.plannedSplits = plannedSplits;
            this.completedSplits = completedSplits;
            this.failureCode = failureCode;
            this.plannedTables = plannedTables;
            this.completedTables = completedTables;
            this.action = action;
        }

        @JsonProperty("step")
        public String step() {
            return step;
        }

        @JsonProperty("status")
        public String status() {
            return status;
        }

        @JsonProperty("progressPercent")
        public Integer progressPercent() {
            return progressPercent;
        }

        @JsonProperty("progressUnit")
        public String progressUnit() {
            return progressUnit;
        }

        @JsonProperty("readRows")
        public Long readRows() {
            return readRows;
        }

        @JsonProperty("committedRows")
        public Long committedRows() {
            return committedRows;
        }

        @JsonProperty("plannedSplits")
        public Integer plannedSplits() {
            return plannedSplits;
        }

        @JsonProperty("completedSplits")
        public Long completedSplits() {
            return completedSplits;
        }

        @JsonProperty("failureCode")
        public String failureCode() {
            return failureCode;
        }

        @JsonProperty("plannedTables")
        public Integer plannedTables() {
            return plannedTables;
        }

        @JsonProperty("completedTables")
        public Integer completedTables() {
            return completedTables;
        }

        @JsonProperty("action")
        public String action() {
            return action;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof StageResult other)) return false;
            return java.util.Objects.equals(step, other.step)
                    && java.util.Objects.equals(status, other.status)
                    && java.util.Objects.equals(progressPercent, other.progressPercent)
                    && java.util.Objects.equals(progressUnit, other.progressUnit)
                    && java.util.Objects.equals(readRows, other.readRows)
                    && java.util.Objects.equals(committedRows, other.committedRows)
                    && java.util.Objects.equals(plannedSplits, other.plannedSplits)
                    && java.util.Objects.equals(completedSplits, other.completedSplits)
                    && java.util.Objects.equals(failureCode, other.failureCode)
                    && java.util.Objects.equals(plannedTables, other.plannedTables)
                    && java.util.Objects.equals(completedTables, other.completedTables)
                    && java.util.Objects.equals(action, other.action);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                    step,
                    status,
                    progressPercent,
                    progressUnit,
                    readRows,
                    committedRows,
                    plannedSplits,
                    completedSplits,
                    failureCode,
                    plannedTables,
                    completedTables,
                    action);
        }

        /** Creates a row-stage observation without schema-preparation counters. */
        public StageResult(
                String step,
                String status,
                Integer progressPercent,
                String progressUnit,
                Long readRows,
                Long committedRows,
                Integer plannedSplits,
                Long completedSplits,
                String failureCode) {
            this(
                    step,
                    status,
                    progressPercent,
                    progressUnit,
                    readRows,
                    committedRows,
                    plannedSplits,
                    completedSplits,
                    failureCode,
                    null,
                    null,
                    null);
        }
    }

    /** Table identity is resolved only from the Instance's immutable mapping version. */
    public static final class TableStages {
        private final int mappingIndex;
        private final List<StageResult> stages;

        @JsonCreator
        public TableStages(
                @JsonProperty("mappingIndex") int mappingIndex, @JsonProperty("stages") List<StageResult> stages) {
            stages = List.copyOf(stages);
            this.mappingIndex = mappingIndex;
            this.stages = stages;
        }

        @JsonProperty("mappingIndex")
        public int mappingIndex() {
            return mappingIndex;
        }

        @JsonProperty("stages")
        public List<StageResult> stages() {
            return stages;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof TableStages other)) return false;
            return java.util.Objects.equals(mappingIndex, other.mappingIndex)
                    && java.util.Objects.equals(stages, other.stages);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(mappingIndex, stages);
        }
    }

    /** Compact observation for list and batch Instance queries without fetching each table report. */
    public static final class RealtimeSummary {
        private final int version;
        private final String observedAt;
        private final long generation;
        private final boolean restored;
        private final String counterScope;
        private final String phase;
        private final Checkpoints checkpoints;
        private final List<StageResult> stages;

        @JsonCreator
        public RealtimeSummary(
                @JsonProperty("version") int version,
                @JsonProperty("observedAt") String observedAt,
                @JsonProperty("generation") long generation,
                @JsonProperty("restored") boolean restored,
                @JsonProperty("counterScope") String counterScope,
                @JsonProperty("phase") String phase,
                @JsonProperty("checkpoints") Checkpoints checkpoints,
                @JsonProperty("stages") List<StageResult> stages) {
            stages = List.copyOf(stages);
            this.version = version;
            this.observedAt = observedAt;
            this.generation = generation;
            this.restored = restored;
            this.counterScope = counterScope;
            this.phase = phase;
            this.checkpoints = checkpoints;
            this.stages = stages;
        }

        @JsonProperty("version")
        public int version() {
            return version;
        }

        @JsonProperty("observedAt")
        public String observedAt() {
            return observedAt;
        }

        @JsonProperty("generation")
        public long generation() {
            return generation;
        }

        @JsonProperty("restored")
        public boolean restored() {
            return restored;
        }

        @JsonProperty("counterScope")
        public String counterScope() {
            return counterScope;
        }

        @JsonProperty("phase")
        public String phase() {
            return phase;
        }

        @JsonProperty("checkpoints")
        public Checkpoints checkpoints() {
            return checkpoints;
        }

        @JsonProperty("stages")
        public List<StageResult> stages() {
            return stages;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof RealtimeSummary other)) return false;
            return java.util.Objects.equals(version, other.version)
                    && java.util.Objects.equals(observedAt, other.observedAt)
                    && java.util.Objects.equals(generation, other.generation)
                    && java.util.Objects.equals(restored, other.restored)
                    && java.util.Objects.equals(counterScope, other.counterScope)
                    && java.util.Objects.equals(phase, other.phase)
                    && java.util.Objects.equals(checkpoints, other.checkpoints)
                    && java.util.Objects.equals(stages, other.stages);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                    version, observedAt, generation, restored, counterScope, phase, checkpoints, stages);
        }
    }

    /** Full report and its bounded summary are persisted together with the terminal Task outcome. */
    public static final class RealtimeReport {
        private final int version;
        private final String observedAt;
        private final long generation;
        private final boolean restored;
        private final String counterScope;
        private final String phase;
        private final Checkpoints checkpoints;
        private final List<StageResult> stages;
        private final List<TableStages> tables;
        private final RealtimeSummary summary;

        @JsonCreator
        public RealtimeReport(
                @JsonProperty("version") int version,
                @JsonProperty("observedAt") String observedAt,
                @JsonProperty("generation") long generation,
                @JsonProperty("restored") boolean restored,
                @JsonProperty("counterScope") String counterScope,
                @JsonProperty("phase") String phase,
                @JsonProperty("checkpoints") Checkpoints checkpoints,
                @JsonProperty("stages") List<StageResult> stages,
                @JsonProperty("tables") List<TableStages> tables,
                @JsonProperty("summary") RealtimeSummary summary) {
            stages = List.copyOf(stages);
            tables = List.copyOf(tables);
            this.version = version;
            this.observedAt = observedAt;
            this.generation = generation;
            this.restored = restored;
            this.counterScope = counterScope;
            this.phase = phase;
            this.checkpoints = checkpoints;
            this.stages = stages;
            this.tables = tables;
            this.summary = summary;
        }

        @JsonProperty("version")
        public int version() {
            return version;
        }

        @JsonProperty("observedAt")
        public String observedAt() {
            return observedAt;
        }

        @JsonProperty("generation")
        public long generation() {
            return generation;
        }

        @JsonProperty("restored")
        public boolean restored() {
            return restored;
        }

        @JsonProperty("counterScope")
        public String counterScope() {
            return counterScope;
        }

        @JsonProperty("phase")
        public String phase() {
            return phase;
        }

        @JsonProperty("checkpoints")
        public Checkpoints checkpoints() {
            return checkpoints;
        }

        @JsonProperty("stages")
        public List<StageResult> stages() {
            return stages;
        }

        @JsonProperty("tables")
        public List<TableStages> tables() {
            return tables;
        }

        @JsonProperty("summary")
        public RealtimeSummary summary() {
            return summary;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof RealtimeReport other)) return false;
            return java.util.Objects.equals(version, other.version)
                    && java.util.Objects.equals(observedAt, other.observedAt)
                    && java.util.Objects.equals(generation, other.generation)
                    && java.util.Objects.equals(restored, other.restored)
                    && java.util.Objects.equals(counterScope, other.counterScope)
                    && java.util.Objects.equals(phase, other.phase)
                    && java.util.Objects.equals(checkpoints, other.checkpoints)
                    && java.util.Objects.equals(stages, other.stages)
                    && java.util.Objects.equals(tables, other.tables)
                    && java.util.Objects.equals(summary, other.summary);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                    version,
                    observedAt,
                    generation,
                    restored,
                    counterScope,
                    phase,
                    checkpoints,
                    stages,
                    tables,
                    summary);
        }
    }

    /** Report fields contain observations only; the matching table identities come from the pinned definition. */
    public static final class Report {
        private final int version;
        private final String observedAt;
        private final List<TableResult> tables;

        @JsonCreator
        public Report(
                @JsonProperty("version") int version,
                @JsonProperty("observedAt") String observedAt,
                @JsonProperty("tables") List<TableResult> tables) {
            tables = List.copyOf(tables);
            this.version = version;
            this.observedAt = observedAt;
            this.tables = tables;
        }

        @JsonProperty("version")
        public int version() {
            return version;
        }

        @JsonProperty("observedAt")
        public String observedAt() {
            return observedAt;
        }

        @JsonProperty("tables")
        public List<TableResult> tables() {
            return tables;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Report other)) return false;
            return java.util.Objects.equals(version, other.version)
                    && java.util.Objects.equals(observedAt, other.observedAt)
                    && java.util.Objects.equals(tables, other.tables);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(version, observedAt, tables);
        }
    }

    /** One mapping-indexed FULL-stage observation. Rows mean emitted and acknowledged input records, not affected rows. */
    public static final class TableResult {
        private final int mappingIndex;
        private final String step;
        private final String status;
        private final Integer progressPercent;
        private final String progressUnit;
        private final long readRows;
        private final long committedRows;
        private final Integer plannedSplits;
        private final long completedSplits;
        private final String failureCode;

        @JsonCreator
        public TableResult(
                @JsonProperty("mappingIndex") int mappingIndex,
                @JsonProperty("step") String step,
                @JsonProperty("status") String status,
                @JsonProperty("progressPercent") Integer progressPercent,
                @JsonProperty("progressUnit") String progressUnit,
                @JsonProperty("readRows") long readRows,
                @JsonProperty("committedRows") long committedRows,
                @JsonProperty("plannedSplits") Integer plannedSplits,
                @JsonProperty("completedSplits") long completedSplits,
                @JsonProperty("failureCode") String failureCode) {
            this.mappingIndex = mappingIndex;
            this.step = step;
            this.status = status;
            this.progressPercent = progressPercent;
            this.progressUnit = progressUnit;
            this.readRows = readRows;
            this.committedRows = committedRows;
            this.plannedSplits = plannedSplits;
            this.completedSplits = completedSplits;
            this.failureCode = failureCode;
        }

        @JsonProperty("mappingIndex")
        public int mappingIndex() {
            return mappingIndex;
        }

        @JsonProperty("step")
        public String step() {
            return step;
        }

        @JsonProperty("status")
        public String status() {
            return status;
        }

        @JsonProperty("progressPercent")
        public Integer progressPercent() {
            return progressPercent;
        }

        @JsonProperty("progressUnit")
        public String progressUnit() {
            return progressUnit;
        }

        @JsonProperty("readRows")
        public long readRows() {
            return readRows;
        }

        @JsonProperty("committedRows")
        public long committedRows() {
            return committedRows;
        }

        @JsonProperty("plannedSplits")
        public Integer plannedSplits() {
            return plannedSplits;
        }

        @JsonProperty("completedSplits")
        public long completedSplits() {
            return completedSplits;
        }

        @JsonProperty("failureCode")
        public String failureCode() {
            return failureCode;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof TableResult other)) return false;
            return java.util.Objects.equals(mappingIndex, other.mappingIndex)
                    && java.util.Objects.equals(step, other.step)
                    && java.util.Objects.equals(status, other.status)
                    && java.util.Objects.equals(progressPercent, other.progressPercent)
                    && java.util.Objects.equals(progressUnit, other.progressUnit)
                    && java.util.Objects.equals(readRows, other.readRows)
                    && java.util.Objects.equals(committedRows, other.committedRows)
                    && java.util.Objects.equals(plannedSplits, other.plannedSplits)
                    && java.util.Objects.equals(completedSplits, other.completedSplits)
                    && java.util.Objects.equals(failureCode, other.failureCode);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                    mappingIndex,
                    step,
                    status,
                    progressPercent,
                    progressUnit,
                    readRows,
                    committedRows,
                    plannedSplits,
                    completedSplits,
                    failureCode);
        }
    }
}
