package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasync.DataSyncMetricsService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsFailureRankVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsStatusMetricVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsSummaryVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsTrendPointVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncOperationsRange;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsFailureStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsMetricsRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsStatusStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsSummaryStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsTrendStats;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * DATA_SYNC 专属的聚合指标与时间桶填补；不把多个Attempt累计为业务吞吐量。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class DataSyncMetricsServiceImpl implements DataSyncMetricsService {

    @Resource
    private DataSyncOperationsMetricsRepository operationsMetricsRepository;

    @Override
    public DataSyncOperationsDashboardVO queryOperationsDashboard(DataSyncOperationsDashboardDTO dto) {
        if (dto == null || dto.getSyncType() == null || dto.getRange() == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "运维指标查询参数不完整");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        LocalDateTime rangeEnd = DateUtils.now();
        LocalDateTime rangeStart = operationsRangeStart(dto.getRange(), rangeEnd);
        DataSyncOperationsSummaryStats summaryStats =
                operationsMetricsRepository.querySummary(workspaceId, dto.getSyncType(), rangeStart, rangeEnd);
        List<DataSyncOperationsTrendStats> trendStats = operationsMetricsRepository.queryTrend(
                workspaceId,
                dto.getSyncType(),
                rangeStart,
                rangeEnd,
                dto.getRange().isHourly());
        List<DataSyncOperationsStatusStats> statusStats = operationsMetricsRepository.queryStatusDistribution(
                workspaceId, dto.getSyncType(), rangeStart, rangeEnd);
        List<DataSyncOperationsFailureStats> failureStats = operationsMetricsRepository.queryFailureRanking(
                workspaceId, dto.getSyncType(), rangeStart, rangeEnd, 5);

        DataSyncOperationsDashboardVO result = new DataSyncOperationsDashboardVO();
        result.setSyncType(dto.getSyncType().name());
        result.setRange(dto.getRange().name());
        result.setRangeStart(rangeStart);
        result.setRangeEnd(rangeEnd);
        result.setSummary(toOperationsSummaryVO(summaryStats));
        result.setTrend(toOperationsTrendVO(trendStats, dto.getRange(), rangeStart, rangeEnd));
        result.setStatusDistribution(toOperationsStatusDistribution(statusStats));
        result.setFailureRanking(
                failureStats.stream().map(this::toOperationsFailureRankVO).toList());
        return result;
    }

    private LocalDateTime operationsRangeStart(DataSyncOperationsRange range, LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        return range == DataSyncOperationsRange.TODAY ? today : today.minusDays(range.getDays() - 1L);
    }

    private DataSyncOperationsSummaryVO toOperationsSummaryVO(DataSyncOperationsSummaryStats source) {
        DataSyncOperationsSummaryVO target = BeanCopyUtils.copy(source, DataSyncOperationsSummaryVO.class);
        target.setExecutionCount(zero(target.getExecutionCount()));
        target.setSucceededCount(zero(target.getSucceededCount()));
        target.setFailedCount(zero(target.getFailedCount()));
        target.setLostCount(zero(target.getLostCount()));
        target.setAbnormalTaskCount(zero(target.getAbnormalTaskCount()));
        target.setCurrentActiveTaskCount(zero(target.getCurrentActiveTaskCount()));
        target.setAutoRecoveryCount(zero(target.getAutoRecoveryCount()));
        target.setReadRows(zero(target.getReadRows()));
        target.setWriteRows(zero(target.getWriteRows()));
        target.setAverageDurationMillis(zero(target.getAverageDurationMillis()));
        return target;
    }

    private List<DataSyncOperationsTrendPointVO> toOperationsTrendVO(
            List<DataSyncOperationsTrendStats> source,
            DataSyncOperationsRange range,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        Map<LocalDateTime, DataSyncOperationsTrendStats> byBucket = new HashMap<>();
        for (DataSyncOperationsTrendStats item : source) {
            if (item.getBucketStart() != null) byBucket.put(item.getBucketStart(), item);
        }

        LocalDateTime bucket = range.isHourly()
                ? rangeStart.truncatedTo(ChronoUnit.HOURS)
                : rangeStart.toLocalDate().atStartOfDay();
        LocalDateTime endBucket = range.isHourly()
                ? rangeEnd.truncatedTo(ChronoUnit.HOURS)
                : rangeEnd.toLocalDate().atStartOfDay();
        List<DataSyncOperationsTrendPointVO> result = new ArrayList<>();
        while (!bucket.isAfter(endBucket)) {
            DataSyncOperationsTrendStats stats = byBucket.get(bucket);
            DataSyncOperationsTrendPointVO point = stats == null
                    ? new DataSyncOperationsTrendPointVO()
                    : BeanCopyUtils.copy(stats, DataSyncOperationsTrendPointVO.class);
            point.setBucketStart(bucket);
            point.setExecutionCount(zero(point.getExecutionCount()));
            point.setSucceededCount(zero(point.getSucceededCount()));
            point.setFailedCount(zero(point.getFailedCount()));
            point.setLostCount(zero(point.getLostCount()));
            point.setAutoRecoveryCount(zero(point.getAutoRecoveryCount()));
            point.setReadRows(zero(point.getReadRows()));
            point.setWriteRows(zero(point.getWriteRows()));
            point.setAverageDurationMillis(zero(point.getAverageDurationMillis()));
            result.add(point);
            bucket = range.isHourly() ? bucket.plusHours(1) : bucket.plusDays(1);
        }
        return result;
    }

    private List<DataSyncOperationsStatusMetricVO> toOperationsStatusDistribution(
            List<DataSyncOperationsStatusStats> source) {
        Map<Integer, Long> countByStatus = new HashMap<>();
        for (DataSyncOperationsStatusStats item : source) {
            if (item.getStatus() != null) countByStatus.put(item.getStatus(), zero(item.getCount()));
        }

        List<DataSyncOperationsStatusMetricVO> result = new ArrayList<>();
        for (DataSyncInstanceStatus status : DataSyncInstanceStatus.values()) {
            DataSyncOperationsStatusMetricVO item = new DataSyncOperationsStatusMetricVO();
            item.setStatus(status.name());
            item.setCount(countByStatus.getOrDefault(status.getValue(), 0L));
            result.add(item);
        }
        return result;
    }

    private DataSyncOperationsFailureRankVO toOperationsFailureRankVO(DataSyncOperationsFailureStats source) {
        DataSyncOperationsFailureRankVO target = BeanCopyUtils.copy(source, DataSyncOperationsFailureRankVO.class);
        target.setFailedCount(zero(target.getFailedCount()));
        target.setLostCount(zero(target.getLostCount()));
        target.setAbnormalCount(zero(target.getAbnormalCount()));
        return target;
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

}
