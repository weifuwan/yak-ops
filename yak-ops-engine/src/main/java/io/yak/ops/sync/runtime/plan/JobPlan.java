package io.yak.ops.sync.runtime.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import java.util.List;
import java.util.Objects;

/**
 * Immutable collection of authorized table routes and their atomic admission claim.
 *
 * <p>Single-table and multi-table jobs use the same collection-based execution path.
 */
public final class JobPlan {
    private final List<TablePlan> tables;
    private final TaskExecutionResources admissionPlan;

    @JsonCreator
    public JobPlan(
            @JsonProperty("tables") List<TablePlan> tables,
            @JsonProperty("admissionPlan") TaskExecutionResources admissionPlan) {
        tables = List.copyOf(tables);
        if (tables.isEmpty()) throw new IllegalArgumentException("Job needs table mappings");
        Objects.requireNonNull(admissionPlan, "admissionPlan");
        this.tables = tables;
        this.admissionPlan = admissionPlan;
    }

    @JsonProperty("tables")
    public List<TablePlan> tables() {
        return tables;
    }

    @JsonProperty("admissionPlan")
    public TaskExecutionResources admissionPlan() {
        return admissionPlan;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof JobPlan other)) return false;
        return java.util.Objects.equals(tables, other.tables)
                && java.util.Objects.equals(admissionPlan, other.admissionPlan);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(tables, admissionPlan);
    }
}
