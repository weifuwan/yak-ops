package io.yak.ops.dao.repository.datasync.impl;

import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.mapper.datasync.DataSyncOperationsMetricsMapper;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsFailureStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsMetricsRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsStatusStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsSummaryStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsTrendStats;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 使用聚合 SQL 实现 Workspace-scoped Data Sync Operations Dashboard 读模型。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Repository
public class DataSyncOperationsMetricsRepositoryImpl implements DataSyncOperationsMetricsRepository {

    @Resource
    private DataSyncOperationsMetricsMapper metricsMapper;

    @Override
    public DataSyncOperationsSummaryStats querySummary(
            String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime) {
        if (!valid(workspaceId, syncType, startTime, endTime)) return new DataSyncOperationsSummaryStats();
        DataSyncOperationsSummaryStats result =
                metricsMapper.selectSummary(workspaceId, syncType.getValue(), startTime, endTime);
        return result == null ? new DataSyncOperationsSummaryStats() : result;
    }

    @Override
    public List<DataSyncOperationsTrendStats> queryTrend(
            String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime, boolean hourly) {
        if (!valid(workspaceId, syncType, startTime, endTime)) return List.of();
        return hourly
                ? metricsMapper.selectHourlyTrend(workspaceId, syncType.getValue(), startTime, endTime)
                : metricsMapper.selectDailyTrend(workspaceId, syncType.getValue(), startTime, endTime);
    }

    @Override
    public List<DataSyncOperationsStatusStats> queryStatusDistribution(
            String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime) {
        if (!valid(workspaceId, syncType, startTime, endTime)) return List.of();
        return metricsMapper.selectStatusDistribution(workspaceId, syncType.getValue(), startTime, endTime);
    }

    @Override
    public List<DataSyncOperationsFailureStats> queryFailureRanking(
            String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        if (!valid(workspaceId, syncType, startTime, endTime)) return List.of();
        return metricsMapper.selectFailureRanking(
                workspaceId, syncType.getValue(), startTime, endTime, Math.max(1, Math.min(20, limit)));
    }

    private boolean valid(String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime) {
        return StringUtils.isNotBlank(workspaceId)
                && syncType != null
                && startTime != null
                && endTime != null
                && startTime.isBefore(endTime);
    }
}
