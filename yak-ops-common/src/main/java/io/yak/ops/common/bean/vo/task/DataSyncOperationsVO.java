package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import java.util.List;

/** Workspace-scoped Data Sync operations read model based on persisted Task Instances. */
public record DataSyncOperationsVO(Summary summary, List<TrendPoint> trend, List<Failure> recentFailures) {

    /** Period counts and a separate all-time snapshot of currently running Instances. */
    public record Summary(
            long executionCount, long succeededCount, long failedCount, long canceledCount, long runningCount) {}

    /** Persisted execution outcomes grouped by the Instance creation time. */
    public record TrendPoint(
            String bucket, long executionCount, long succeededCount, long failedCount, long canceledCount) {}

    /** Failed Instance entry linking to its Task Definition and Instance details. */
    public record Failure(
            String instanceId, String definitionId, String taskName, String failureCode, LocalDateTime createTime) {}
}
