package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import java.util.Objects;

/**
 * Immutable DATA_SYNC plan containing pinned endpoint parameters, resolved table metadata and
 * admitted resource claims.
 *
 * <p>This value contains no connection credentials, physical Connector instances or running
 * execution handles. Runtime connections are resolved only after admission.
 *
 * @param sourceParameters selected Source definition
 * @param sinkParameters selected Sink definition
 * @param source authorized Source table and logical schema
 * @param target authorized target table and logical schema
 * @param admissionPlan exclusive target claim and resource binding fingerprint
 * @param sourceOptions non-secret Source operation and prepared replication settings
 */
public record JobPlan(
        ReadonlyConfig sourceParameters,
        ReadonlyConfig sinkParameters,
        DataSourceTable source,
        DataSourceTable target,
        TaskAdmissionPlan admissionPlan,
        ReadonlyConfig sourceOptions) {

    public JobPlan {
        Objects.requireNonNull(sourceParameters, "sourceParameters");
        Objects.requireNonNull(sinkParameters, "sinkParameters");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(admissionPlan, "admissionPlan");
        Objects.requireNonNull(sourceOptions, "sourceOptions");
    }
}
