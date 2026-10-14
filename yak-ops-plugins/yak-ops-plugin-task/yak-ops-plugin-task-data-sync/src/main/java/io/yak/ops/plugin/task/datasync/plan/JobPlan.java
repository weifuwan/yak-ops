package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import java.util.List;
import java.util.Objects;

/** Complete immutable table plan and atomic resource admission for one physical Job. */
public record JobPlan(List<TablePlan> tables, TaskAdmissionPlan admissionPlan) {
    public JobPlan {
        tables = List.copyOf(tables);
        if (tables.isEmpty()) throw new IllegalArgumentException("Job needs table mappings");
        Objects.requireNonNull(admissionPlan, "admissionPlan");
    }

    /** Constructs a single-table plan using the same collection-based execution contract. */
    public JobPlan(
            ReadonlyConfig sourceParameters,
            ReadonlyConfig sinkParameters,
            DataSourceTable source,
            DataSourceTable target,
            TaskAdmissionPlan admissionPlan,
            ReadonlyConfig sourceOptions) {
        this(List.of(new TablePlan(sourceParameters, sinkParameters, source, target, sourceOptions)), admissionPlan);
    }

    private TablePlan single() {
        if (tables.size() != 1) throw new IllegalStateException("Job has multiple table mappings");
        return tables.getFirst();
    }

    public ReadonlyConfig sourceParameters() {
        return single().sourceParameters();
    }

    public ReadonlyConfig sinkParameters() {
        return single().sinkParameters();
    }

    public DataSourceTable source() {
        return single().source();
    }

    public DataSourceTable target() {
        return single().target();
    }

    public ReadonlyConfig sourceOptions() {
        return single().sourceOptions();
    }
}
