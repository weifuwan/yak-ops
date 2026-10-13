package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import java.util.Objects;

/** Non-secret physical table metadata and the canonical resource claim of one execution. */
public record SyncPipelinePlan(SyncRuntimeTable source, SyncRuntimeTable target, TaskExecutionPlan resourcePlan) {

    public SyncPipelinePlan {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(resourcePlan, "resourcePlan");
    }
}
