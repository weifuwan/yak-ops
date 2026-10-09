package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.enums.datasync.DataSyncType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定义 Workspace-scoped Data Sync Operations Dashboard 聚合查询。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
public interface DataSyncOperationsMetricsRepository {

    DataSyncOperationsSummaryStats querySummary(
            String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime);

    List<DataSyncOperationsTrendStats> queryTrend(
            String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime, boolean hourly);

    List<DataSyncOperationsStatusStats> queryStatusDistribution(
            String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime);

    List<DataSyncOperationsFailureStats> queryFailureRanking(
            String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime, int limit);
}
