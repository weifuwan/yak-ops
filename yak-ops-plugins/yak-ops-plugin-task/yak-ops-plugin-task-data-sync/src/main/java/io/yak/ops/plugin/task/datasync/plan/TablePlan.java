package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.Objects;

/** Resolved source-to-target metadata and non-secret options for one explicit table mapping. */
public record TablePlan(
        ReadonlyConfig sourceParameters,
        ReadonlyConfig sinkParameters,
        DataSourceTable source,
        DataSourceTable target,
        ReadonlyConfig sourceOptions) {
    public TablePlan {
        Objects.requireNonNull(sourceParameters, "sourceParameters");
        Objects.requireNonNull(sinkParameters, "sinkParameters");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sourceOptions, "sourceOptions");
    }
}
