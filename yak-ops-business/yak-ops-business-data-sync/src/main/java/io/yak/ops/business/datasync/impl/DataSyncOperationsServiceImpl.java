package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasync.DataSyncOperationsService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.business.datasync.scheduler.ScheduleEngineException;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRetryPolicyDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsFailureRankVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsStatusMetricVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsSummaryVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsTrendPointVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncOperationsRange;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsFailureStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsMetricsRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsStatusStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsSummaryStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsTrendStats;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskPageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DataSyncOperationsService 的业务职责实现。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class DataSyncOperationsServiceImpl implements DataSyncOperationsService {

    @Resource
    private DataSyncTaskRepository taskRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncScheduleRepository scheduleRepository;

    @Resource
    private DataSyncOperationsMetricsRepository operationsMetricsRepository;

    @Resource
    private ScheduleEngine scheduleEngine;

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncOperationsServiceImpl.class);

    @Override
    public PagingData<DataSyncTaskOperationVO> queryTaskOperationPage(DataSyncTaskQueryDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY);
        if (CollectionUtils.isNotEmpty(dto.getSorts())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "运维任务分页暂不支持自定义排序");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskPageQuery query = new DataSyncTaskPageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getKeyword()),
                dto.getSyncType(),
                DataSyncTaskStatus.PUBLISHED,
                StringUtils.trimToNull(dto.getSourceDataSourceId()),
                StringUtils.trimToNull(dto.getTargetDataSourceId()));
        return PagingData.from(
                taskRepository.queryPage(workspaceId, query).map(task -> toTaskOperationVO(workspaceId, task)));
    }

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

    private DataSyncTaskOperationVO toTaskOperationVO(String workspaceId, DataSyncTaskEntity task) {
        DataSyncTaskOperationVO target = new DataSyncTaskOperationVO();
        target.setId(task.getId());
        target.setName(task.getName());
        target.setSyncType(
                task.getSyncType() == null ? null : task.getSyncType().name());
        target.setDesiredState(taskDesiredState(task).name());
        target.setDefinitionVersion(task.getDefinitionVersion());
        target.setRetryPolicy(toRetryPolicyVO(task.getRetryPolicy()));
        instanceRepository
                .queryLatestByTask(workspaceId, task.getId())
                .ifPresent(instance -> target.setLatestInstance(toInstanceSummaryVO(instance)));
        if (task.getSyncType() == DataSyncType.OFFLINE) {
            scheduleRepository
                    .queryByTask(workspaceId, task.getId())
                    .ifPresent(schedule -> target.setSchedule(toRuntimeScheduleVO(schedule)));
        }
        return target;
    }

    private DataSyncScheduleVO toScheduleVO(DataSyncScheduleEntity entity) {
        return BeanCopyUtils.copy(entity, DataSyncScheduleVO.class);
    }

    private DataSyncScheduleVO toRuntimeScheduleVO(DataSyncScheduleEntity entity) {
        DataSyncScheduleVO target = toScheduleVO(entity);
        if (!Boolean.TRUE.equals(entity.getEnabled())) return target;
        try {
            scheduleEngine
                    .queryNextFireTime(entity.getId())
                    .ifPresent(nextFireTime -> target.setNextFireTime(
                            LocalDateTime.ofInstant(nextFireTime, ZoneId.of(entity.getTimeZone()))));
        } catch (ScheduleEngineException exception) {
            LOG.warn("查询调度下一次触发时间失败，scheduleId={}, error={}", entity.getId(), exception.getMessage());
        }
        return target;
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

    private DataSyncDesiredState taskDesiredState(DataSyncTaskEntity task) {
        return task.getDesiredState() == null ? DataSyncDesiredState.STOPPED : task.getDesiredState();
    }

    private DataSyncRetryPolicyDTO retryPolicyConfig(String json) {
        DataSyncRetryPolicyDTO policy = StringUtils.isBlank(json)
                ? new DataSyncRetryPolicyDTO()
                : JSONUtils.parseObject(json, DataSyncRetryPolicyDTO.class);
        normalizeRetryPolicy(policy);
        return policy;
    }

    private void normalizeRetryPolicy(DataSyncRetryPolicyDTO policy) {
        if (policy.getMode() == null) {
            policy.setMode(DataSyncRetryPolicyMode.FIXED);
        }
    }

    private DataSyncRetryPolicyVO toRetryPolicyVO(String json) {
        return BeanCopyUtils.copy(retryPolicyConfig(json), DataSyncRetryPolicyVO.class);
    }

    private DataSyncInstanceVO toInstanceSummaryVO(DataSyncInstanceEntity source) {
        DataSyncInstanceVO target = BeanCopyUtils.copy(
                source, DataSyncInstanceVO.class, "syncType", "triggerType", "status", "definitionSnapshot");
        target.setSyncType(source.getSyncType() == null ? null : source.getSyncType().name());
        target.setTriggerType(source.getTriggerType() == null ? null : source.getTriggerType().name());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        return target;
    }
}
