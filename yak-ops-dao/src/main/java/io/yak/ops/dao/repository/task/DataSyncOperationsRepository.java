package io.yak.ops.dao.repository.task;

import io.yak.ops.common.bean.vo.task.DataSyncOperationsVO;
import java.time.LocalDateTime;
import java.util.List;

/** Aggregated, Workspace-scoped Data Sync Instance reads from immutable Definition versions. */
public interface DataSyncOperationsRepository {

    /**
     * Aggregates Task Instance outcomes created in the selected period.
     *
     * @param workspaceId owning Workspace
     * @param sourceType immutable source type (JDBC or MYSQL_CDC)
     * @param start period start, inclusive
     * @param end period end, exclusive
     * @return outcome counts
     */
    DataSyncOperationsVO.Summary summary(String workspaceId, String sourceType, LocalDateTime start, LocalDateTime end);

    /**
     * Counts currently running Instances independently of their creation date.
     *
     * @param workspaceId owning Workspace
     * @param sourceType immutable source type
     * @return current RUNNING Instance count
     */
    long runningCount(String workspaceId, String sourceType);

    /**
     * Groups Task Instance outcomes by their creation-time bucket.
     *
     * @param workspaceId owning Workspace
     * @param sourceType immutable source type
     * @param start period start, inclusive
     * @param end period end, exclusive
     * @param hourly whether to group by hour instead of day
     * @return ordered persisted buckets
     */
    List<DataSyncOperationsVO.TrendPoint> trend(
            String workspaceId, String sourceType, LocalDateTime start, LocalDateTime end, boolean hourly);

    /**
     * Returns the latest failures in the selected period.
     *
     * @param workspaceId owning Workspace
     * @param sourceType immutable source type
     * @param start period start, inclusive
     * @param end period end, exclusive
     * @return up to five failed Instance identities
     */
    List<DataSyncOperationsVO.Failure> recentFailures(
            String workspaceId, String sourceType, LocalDateTime start, LocalDateTime end);
}
