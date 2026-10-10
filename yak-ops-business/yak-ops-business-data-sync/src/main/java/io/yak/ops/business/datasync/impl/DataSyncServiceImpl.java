package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleDefinition;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFire;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFireListener;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.business.datasync.scheduler.ScheduleEngineException;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRealtimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRetryPolicyDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRuntimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTableRouteDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsFailureRankVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsStatusMetricVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsSummaryVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsTrendPointVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRealtimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSinkTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSourceTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableExecutionVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableRouteVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTracePageVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTraceSummaryVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncOperationsRange;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncExecutionEventEntity;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncExecutionEventRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstancePageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsFailureStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsMetricsRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsStatusStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsSummaryStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsTrendStats;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableRouteRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskPageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import jakarta.annotation.Resource;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 实现 Workspace-scoped Data Sync 任务定义持久化和任务/实例查询。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Service
public class DataSyncServiceImpl implements DataSyncService, DataSyncScheduleFireListener {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncServiceImpl.class);
    private static final Set<String> REALTIME_TARGET_TYPES = Set.of("MYSQL", "POSTGRE_SQL", "ORACLE");
    private static final int DEFAULT_TRACE_PAGE_SIZE = 50;

    @Resource
    private DataSyncTaskRepository taskRepository;

    @Resource
    private DataSyncTableRouteRepository tableRouteRepository;

    @Resource
    private DataSyncTableRouteDefinitionService routeDefinitionService;

    @Resource
    private DataSyncTableExecutionRepository tableExecutionRepository;

    @Resource
    private DataSyncTableAttemptRepository tableAttemptRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncAttemptRepository attemptRepository;

    @Resource
    private DataSyncExecutionEventRepository executionEventRepository;

    @Resource
    private DataSyncOperationsMetricsRepository operationsMetricsRepository;

    @Resource
    private DataSyncScheduleRepository scheduleRepository;

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private ScheduleEngine scheduleEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO createTask(DataSyncTaskDTO dto) {
        return createTask(dto, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO createTask(DataSyncTaskDTO dto, String operatorUserId) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String name = StringUtils.trimToNull(dto.getName());
        if (name == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, null);
        DataSyncType syncType = requireSyncType(dto.getSyncType());
        materializeCreatePolicies(syncType, dto);
        List<DataSyncTableRouteDTO> requestedRoutes = prepareExplicitTableRoutes(dto);
        if (requestedRoutes != null) routeDefinitionService.requireOwnedIds(workspaceId, null, requestedRoutes);
        DataSyncTableRouteDTO resolvedScope = resolveTaskScope(dto);
        validateTaskDefinition(syncType, dto, resolvedScope);

        DataSyncTaskEntity entity = new DataSyncTaskEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setName(name);
        entity.setSyncType(syncType);
        entity.setStatus(DataSyncTaskStatus.UNPUBLISHED);
        entity.setDesiredState(DataSyncDesiredState.STOPPED);
        applyDefinition(entity, dto, resolvedScope);
        entity.setDefinitionVersion(1);
        entity.initCreate(operatorUserId);

        if (taskRepository.add(entity) == null) {
            throw new DataSyncException(DataSyncErrorCode.CREATE_TASK_FAILED);
        }
        if (requestedRoutes == null) {
            createCompatibilityTableRoute(entity, operatorUserId);
        } else {
            routeDefinitionService.reconcile(workspaceId, entity.getId(), requestedRoutes, operatorUserId);
        }
        return toTaskVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto) {
        return updateTask(id, dto, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto, String operatorUserId) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        requireTaskStatus(entity, DataSyncTaskStatus.UNPUBLISHED, "已上线任务请先下线后再编辑");
        String name = StringUtils.trimToNull(dto.getName());
        if (name == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, id);
        DataSyncType syncType = requireSyncType(dto.getSyncType());
        if (entity.getSyncType() != syncType) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "同步类型创建后不允许修改");
        }
        materializeUpdatePolicies(entity, dto);
        List<DataSyncTableRouteDTO> requestedRoutes = prepareExplicitTableRoutes(dto);
        if (requestedRoutes != null) routeDefinitionService.requireOwnedIds(workspaceId, id, requestedRoutes);
        DataSyncTableRouteDTO resolvedScope = resolveTaskScope(dto);
        validateTaskDefinition(syncType, dto, resolvedScope);

        boolean executableDefinitionChanged = executableDefinitionChanged(entity, dto, resolvedScope)
                || (requestedRoutes != null && routeDefinitionService.changed(workspaceId, id, requestedRoutes));
        entity.setName(name);
        applyDefinition(entity, dto, resolvedScope);
        if (executableDefinitionChanged) {
            entity.setDefinitionVersion(Math.max(1, entity.getDefinitionVersion()) + 1);
        }
        entity.initUpdate(operatorUserId);

        if (taskRepository.update(workspaceId, entity) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED);
        }
        if (requestedRoutes == null) {
            synchronizeCompatibilityTableRoute(entity, operatorUserId);
        } else {
            routeDefinitionService.reconcile(workspaceId, id, requestedRoutes, operatorUserId);
        }
        return toTaskVO(entity);
    }

    @Override
    public DataSyncTaskVO queryTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return toTaskVO(requireTask(workspaceId, id));
    }

    @Override
    public PagingData<DataSyncTaskVO> queryTaskPage(DataSyncTaskQueryDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY);
        if (CollectionUtils.isNotEmpty(dto.getSorts())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "任务分页暂不支持自定义排序");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskPageQuery query = new DataSyncTaskPageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getKeyword()),
                dto.getSyncType(),
                dto.getStatus(),
                StringUtils.trimToNull(dto.getSourceDataSourceId()),
                StringUtils.trimToNull(dto.getTargetDataSourceId()));
        PageData<DataSyncTaskEntity> page = taskRepository.queryPage(workspaceId, query);
        List<String> offlineTaskIds = page.records().stream()
                .filter(task -> task.getSyncType() == DataSyncType.OFFLINE)
                .map(DataSyncTaskEntity::getId)
                .toList();
        Map<String, DataSyncScheduleEntity> scheduleByTask = new HashMap<>();
        if (!offlineTaskIds.isEmpty()) {
            scheduleRepository
                    .queryByTasks(workspaceId, offlineTaskIds)
                    .forEach(schedule -> scheduleByTask.put(schedule.getTaskId(), schedule));
        }
        return PagingData.from(page.map(task -> toTaskListVO(task, scheduleByTask.get(task.getId()))));
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO publishTask(String id) {
        return publishTask(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO publishTask(String id, String operatorUserId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, id);
        requireTaskStatus(task, DataSyncTaskStatus.UNPUBLISHED, "任务已经上线");
        validatePersistedTaskDefinition(task);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.initUpdate(operatorUserId);
        if (taskRepository.update(workspaceId, task) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "上线任务失败");
        }
        return toTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO unpublishTask(String id) {
        return unpublishTask(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO unpublishTask(String id, String operatorUserId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, id);
        requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "任务已经下线");
        if (instanceRepository.existsActiveByTask(workspaceId, task.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS, "请先停止当前运行实例再下线任务");
        }
        disableScheduleForTask(workspaceId, task.getId());
        task.setDesiredState(DataSyncDesiredState.STOPPED);
        task.setStatus(DataSyncTaskStatus.UNPUBLISHED);
        task.initUpdate(operatorUserId);
        if (taskRepository.update(workspaceId, task) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "下线任务失败");
        }
        return toTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized DataSyncInstanceVO runTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, id);
        requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "任务尚未上线");
        validatePersistedTaskDefinition(task);
        if (instanceRepository.existsActiveByTask(workspaceId, task.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS);
        }
        throw runtimeUnavailable();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO saveSchedule(String taskId, DataSyncScheduleDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, taskId);
        requireOfflineTask(task);

        String cronExpression = StringUtils.trimToNull(dto.getCronExpression());
        String timeZone = normalizeTimeZone(dto.getTimeZone());
        if (cronExpression == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不能为空");
        }

        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) {
            schedule = new DataSyncScheduleEntity();
            schedule.setWorkspaceId(workspaceId);
            schedule.setTaskId(taskId);
            schedule.setEnabled(false);
            schedule.setCronExpression(cronExpression);
            schedule.setTimeZone(timeZone);
            schedule.initCreate();
            validateScheduleDefinition(toScheduleDefinition(schedule));
            if (scheduleRepository.add(schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        } else {
            schedule.setCronExpression(cronExpression);
            schedule.setTimeZone(timeZone);
            schedule.initUpdate();
            validateScheduleDefinition(toScheduleDefinition(schedule));
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
            if (Boolean.TRUE.equals(schedule.getEnabled())) {
                requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "启用中的调度要求任务保持上线");
                replaceScheduleAfterCommit(schedule);
            }
        }
        return toScheduleVO(schedule);
    }

    @Override
    public DataSyncSchedulePreviewVO previewSchedule(DataSyncScheduleDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);

        String cronExpression = StringUtils.trimToNull(dto.getCronExpression());
        String timeZone = normalizeTimeZone(dto.getTimeZone());
        if (cronExpression == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不能为空");
        }

        ZoneId zoneId = ZoneId.of(timeZone);
        List<LocalDateTime> nextFireTimes;
        try {
            nextFireTimes = scheduleEngine.previewNextFireTimes(cronExpression, zoneId, 5).stream()
                    .map(instant -> LocalDateTime.ofInstant(instant, zoneId))
                    .toList();
        } catch (ScheduleEngineException | IllegalArgumentException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不合法", exception);
        }

        DataSyncSchedulePreviewVO result = new DataSyncSchedulePreviewVO();
        result.setCronExpression(cronExpression);
        result.setTimeZone(timeZone);
        result.setNextFireTimes(nextFireTimes);
        return result;
    }

    @Override
    public DataSyncScheduleVO querySchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireOfflineTask(requireTask(workspaceId, taskId));
        return scheduleRepository
                .queryByTask(workspaceId, taskId)
                .map(this::toScheduleVO)
                .orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO enableSchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, taskId);
        requireOfflineTask(task);
        requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "任务上线后才能启用调度");

        DataSyncScheduleEntity schedule = requireSchedule(workspaceId, taskId);
        validateScheduleDefinition(toScheduleDefinition(schedule));
        if (!Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(true);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        replaceScheduleAfterCommit(schedule);
        return toScheduleVO(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO disableSchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireOfflineTask(requireTask(workspaceId, taskId));
        DataSyncScheduleEntity schedule = requireSchedule(workspaceId, taskId);
        if (Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(false);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        unscheduleAfterCommit(schedule.getId());
        return toScheduleVO(schedule);
    }

    @Override
    public void restoreScheduleRuntime() {
        for (DataSyncScheduleEntity schedule : scheduleRepository.queryEnabled()) {
            DataSyncTaskEntity task = taskRepository
                    .queryById(schedule.getWorkspaceId(), schedule.getTaskId())
                    .orElseThrow(() -> new DataSyncException(
                            DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "启用中的调度关联任务不存在，scheduleId=" + schedule.getId()));
            requireOfflineTask(task);
            requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "启用中的调度关联任务必须保持上线");
            validateScheduleDefinition(toScheduleDefinition(schedule));
            replaceScheduleRuntime(schedule);
        }
    }

    @Override
    public void restoreRealtimeDesiredState() {
        // 历史 desired-state 作为产品定义保留；新 Runtime 尚未接入，不自动创建 Execution。
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void onFire(DataSyncScheduleFire fire) {
        if (fire == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);
        WorkspaceContext.bind(fire.workspaceId());
        try {
            DataSyncScheduleEntity schedule = scheduleRepository
                    .queryById(fire.workspaceId(), fire.scheduleId())
                    .orElse(null);
            if (schedule == null
                    || !Boolean.TRUE.equals(schedule.getEnabled())
                    || !Objects.equals(schedule.getTaskId(), fire.taskId())) {
                return;
            }
            DataSyncTaskEntity task =
                    taskRepository.queryById(fire.workspaceId(), fire.taskId()).orElse(null);
            if (task == null
                    || task.getSyncType() != DataSyncType.OFFLINE
                    || task.getStatus() != DataSyncTaskStatus.PUBLISHED
                    || instanceRepository.existsActiveByTask(fire.workspaceId(), fire.taskId())) {
                return;
            }
            validatePersistedTaskDefinition(task);
            throw runtimeUnavailable();
        } finally {
            WorkspaceContext.clear();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        requireTaskStatus(entity, DataSyncTaskStatus.UNPUBLISHED, "已上线任务请先下线后再删除");
        if (instanceRepository.existsActiveByTask(workspaceId, entity.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS);
        }
        deleteScheduleForTask(workspaceId, entity.getId());
        if (taskRepository.deleteById(workspaceId, entity.getId()) <= 0) {
            throw new DataSyncException(DataSyncErrorCode.DELETE_TASK_FAILED);
        }
        tableRouteRepository.deleteByTask(workspaceId, entity.getId());
        return true;
    }

    @Override
    public DataSyncInstanceVO queryInstance(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return toInstanceVO(requireInstance(workspaceId, id), true);
    }

    @Override
    public List<DataSyncAttemptVO> queryAttempts(String instanceId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireInstance(workspaceId, instanceId);
        return attemptRepository.queryByExecution(workspaceId, instanceId).stream()
                .map(this::toAttemptVO)
                .toList();
    }

    @Override
    public List<DataSyncTableAttemptVO> queryTableAttempts(String instanceId, String tableExecutionId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireInstance(workspaceId, instanceId);
        DataSyncTableExecutionEntity table = tableExecutionRepository
                .queryById(workspaceId, tableExecutionId)
                .filter(value -> instanceId.equals(value.getExecutionId()))
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_FOUND));
        return tableAttemptRepository.queryByTableExecution(workspaceId, table.getId()).stream()
                .map(this::toTableAttemptVO)
                .toList();
    }

    @Override
    public List<DataSyncExecutionEventVO> queryExecutionEvents(String instanceId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireInstance(workspaceId, instanceId);
        return executionEventRepository.queryByExecution(workspaceId, instanceId).stream()
                .map(this::toExecutionEventVO)
                .toList();
    }

    @Override
    public DataSyncTraceSummaryVO queryExecutionTraceSummary(String instanceId, Integer attemptNo) {
        requireInstance(WorkspaceContext.requireWorkspaceId(), instanceId);
        throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "旧同步引擎 Trace 已移除");
    }

    @Override
    public DataSyncTracePageVO<DataSyncSourceTraceVO> queryExecutionSourceTrace(
            String instanceId, Integer attemptNo, Integer pageSize, String cursor, String status) {
        requireInstance(WorkspaceContext.requireWorkspaceId(), instanceId);
        throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "旧同步引擎 Trace 已移除");
    }

    @Override
    public DataSyncTracePageVO<DataSyncSinkTraceVO> queryExecutionSinkTrace(
            String instanceId, Integer attemptNo, Integer pageSize, String cursor, String status) {
        requireInstance(WorkspaceContext.requireWorkspaceId(), instanceId);
        throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "旧同步引擎 Trace 已移除");
    }

    @Override
    public PagingData<DataSyncInstanceVO> queryInstancePage(DataSyncInstanceQueryDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY);
        if (CollectionUtils.isNotEmpty(dto.getSorts())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "实例分页暂不支持自定义排序");
        }
        if (dto.getStartTimeStart() != null
                && dto.getStartTimeEnd() != null
                && dto.getStartTimeStart().isAfter(dto.getStartTimeEnd())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "开始时间范围不合法");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncInstancePageQuery query = new DataSyncInstancePageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getTaskId()),
                StringUtils.trimToNull(dto.getKeyword()),
                dto.getSyncType(),
                dto.getStatus(),
                dto.getTriggerType(),
                dto.getStartTimeStart(),
                dto.getStartTimeEnd());
        return PagingData.from(
                instanceRepository.queryPage(workspaceId, query).map(value -> toInstanceVO(value, false)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncInstanceVO cancelInstance(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncInstanceEntity instance = requireInstance(workspaceId, id);
        DataSyncInstanceStatus original = instance.getStatus();
        if (original == null || original.isTerminal()) {
            return toInstanceVO(instance, true);
        }

        if (instance.getSyncType() == DataSyncType.REALTIME) {
            DataSyncTaskEntity task =
                    taskRepository.queryById(workspaceId, instance.getTaskId()).orElse(null);
            if (task != null) updateDesiredState(workspaceId, task, DataSyncDesiredState.STOPPED);
        }

        LocalDateTime now = DateUtils.now();
        DataSyncTableExecutionStatus tableStatus;
        if (original == DataSyncInstanceStatus.PENDING || original == DataSyncInstanceStatus.RETRY_WAITING) {
            if (!instanceRepository.cancelExecution(workspaceId, id, original, now)) {
                throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
            }
            attemptRepository.cancelActiveByExecution(workspaceId, id, now);
            tableStatus = DataSyncTableExecutionStatus.CANCELED;
        } else if (original == DataSyncInstanceStatus.RUNNING) {
            // 旧执行引擎已移除：不能假装已将运行任务取消成功，只能按遗留运行态收口 LOST。
            if (!instanceRepository.transitionStatus(
                    workspaceId,
                    id,
                    DataSyncInstanceStatus.RUNNING,
                    DataSyncInstanceStatus.LOST,
                    null,
                    now,
                    DataSyncErrorCode.EXECUTION_LOST.getCode(),
                    DataSyncErrorCode.EXECUTION_LOST.getMessage())) {
                throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
            }
            for (DataSyncAttemptEntity attempt : attemptRepository.queryByExecution(workspaceId, id)) {
                if (attempt.getStatus() != null && !attempt.getStatus().isTerminal()) {
                    attemptRepository.transitionStatus(
                            workspaceId,
                            attempt.getId(),
                            attempt.getStatus(),
                            DataSyncAttemptStatus.LOST,
                            null,
                            now,
                            DataSyncErrorCode.EXECUTION_LOST.getCode(),
                            DataSyncErrorCode.EXECUTION_LOST.getMessage());
                }
            }
            tableStatus = DataSyncTableExecutionStatus.LOST;
        } else {
            throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
        }

        tableExecutionRepository.finishUnfinished(
                workspaceId,
                id,
                tableStatus,
                now,
                tableStatus == DataSyncTableExecutionStatus.LOST ? DataSyncErrorCode.EXECUTION_LOST.getCode() : null,
                tableStatus == DataSyncTableExecutionStatus.LOST
                        ? DataSyncErrorCode.EXECUTION_LOST.getMessage()
                        : null);
        for (DataSyncTableExecutionEntity table : tableExecutionRepository.queryByExecution(workspaceId, id)) {
            if (tableStatus == DataSyncTableExecutionStatus.LOST) {
                tableAttemptRepository.markActiveAsLost(
                        workspaceId,
                        table.getId(),
                        now,
                        DataSyncErrorCode.EXECUTION_LOST.getCode(),
                        DataSyncErrorCode.EXECUTION_LOST.getMessage());
            } else {
                tableAttemptRepository.cancelActive(workspaceId, table.getId(), now);
            }
        }
        return toInstanceVO(requireInstance(workspaceId, id), true);
    }

    private void requireOfflineTask(DataSyncTaskEntity task) {
        if (task == null || task.getSyncType() != DataSyncType.OFFLINE) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "只有离线同步任务支持 Cron 调度");
        }
    }

    private DataSyncScheduleEntity requireSchedule(String workspaceId, String taskId) {
        return scheduleRepository
                .queryByTask(workspaceId, taskId)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.SCHEDULE_NOT_FOUND));
    }

    private String normalizeTimeZone(String value) {
        String timeZone = StringUtils.trimToNull(value);
        if (timeZone == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "时区不能为空");
        try {
            return ZoneId.of(timeZone).getId();
        } catch (DateTimeException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "时区不合法", exception);
        }
    }

    private DataSyncScheduleDefinition toScheduleDefinition(DataSyncScheduleEntity schedule) {
        try {
            return new DataSyncScheduleDefinition(
                    schedule.getId(),
                    schedule.getWorkspaceId(),
                    schedule.getTaskId(),
                    schedule.getCronExpression(),
                    ZoneId.of(schedule.getTimeZone()));
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "调度定义不完整", exception);
        }
    }

    private void validateScheduleDefinition(DataSyncScheduleDefinition definition) {
        try {
            scheduleEngine.validate(definition);
        } catch (ScheduleEngineException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不合法", exception);
        }
    }

    private void replaceScheduleAfterCommit(DataSyncScheduleEntity schedule) {
        runAfterCommit(() -> replaceScheduleRuntime(schedule));
    }

    private void unscheduleAfterCommit(String scheduleId) {
        runAfterCommit(() -> {
            try {
                scheduleEngine.unschedule(scheduleId);
            } catch (ScheduleEngineException exception) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "移除调度失败", exception);
            }
        });
    }

    private void replaceScheduleRuntime(DataSyncScheduleEntity schedule) {
        try {
            scheduleEngine.unschedule(schedule.getId());
            scheduleEngine.schedule(toScheduleDefinition(schedule));
        } catch (ScheduleEngineException exception) {
            throw new DataSyncException(DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "注册调度失败", exception);
        }
    }

    private void disableScheduleForTask(String workspaceId, String taskId) {
        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) return;
        if (Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(false);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        unscheduleAfterCommit(schedule.getId());
    }

    private void deleteScheduleForTask(String workspaceId, String taskId) {
        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) return;
        if (scheduleRepository.deleteByTask(workspaceId, taskId) <= 0) {
            throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED, "删除任务调度失败");
        }
        unscheduleAfterCommit(schedule.getId());
    }

    private DataSyncScheduleVO toScheduleVO(DataSyncScheduleEntity entity) {
        return BeanCopyUtils.copy(entity, DataSyncScheduleVO.class);
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
                .ifPresent(instance -> target.setLatestInstance(toInstanceVO(instance, false)));
        if (task.getSyncType() == DataSyncType.OFFLINE) {
            scheduleRepository
                    .queryByTask(workspaceId, task.getId())
                    .ifPresent(schedule -> target.setSchedule(toRuntimeScheduleVO(schedule)));
        }
        return target;
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

    private List<DataSyncTableRouteEntity> validatePersistedTaskDefinition(DataSyncTaskEntity task) {
        List<DataSyncTableRouteEntity> routes = requirePersistedTableRoutes(task);
        DataSyncWriteMode mode = taskWriteMode(task);
        validateWriteMode(task.getSyncType(), mode);
        if (routes.size() > 1 && task.getSyncType() == DataSyncType.REALTIME) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "REALTIME 当前只支持单 Route");
        }
        // Old V3/V4 policies cannot silently become same-name writes.
        rejectLegacyPolicies(task.getAutoCreateTable(), task.getMappingConfig());
        for (DataSyncTableRouteEntity route : routes) {
            rejectLegacyPolicies(route.getAutoCreateTable(), route.getMappingConfig());
            DataSyncTableRouteDTO scope = resolveRouteScope(task, route);
            if (task.getSyncType() == DataSyncType.REALTIME) {
                validateRealtimeDatasourceTypes(task.getSourceDataSourceId(), task.getTargetDataSourceId());
            }
            validateRouteTables(
                    task.getSourceDataSourceId(), task.getTargetDataSourceId(), scope, task.getSyncType(), mode);
        }
        return routes;
    }

    private void rejectLegacyPolicies(Boolean autoCreate, String mappingJson) {
        if (Boolean.TRUE.equals(autoCreate) || StringUtils.isNotBlank(mappingJson)) {
            throw new DataSyncException(
                    DataSyncErrorCode.INVALID_TASK, "任务包含已下线的字段映射或自动建表配置，请重新编辑并保存后再上线");
        }
    }

    private List<DataSyncTableRouteEntity> requirePersistedTableRoutes(DataSyncTaskEntity task) {
        List<DataSyncTableRouteEntity> routes =
                new ArrayList<>(tableRouteRepository.queryByTask(task.getWorkspaceId(), task.getId()));
        if (routes.isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "同步任务缺少 Table Route");
        }
        routes.sort(
                Comparator.comparing(DataSyncTableRouteEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DataSyncTableRouteEntity::getId, Comparator.nullsLast(String::compareTo)));
        for (int index = 0; index < routes.size(); index++) {
            DataSyncTableRouteEntity route = routes.get(index);
            if (!Objects.equals(route.getWorkspaceId(), task.getWorkspaceId())
                    || !Objects.equals(route.getTaskId(), task.getId())
                    || StringUtils.isBlank(route.getId())
                    || route.getSortOrder() == null
                    || route.getSortOrder() != index) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "Table Route 身份或顺序不合法");
            }
        }
        return List.copyOf(routes);
    }

    private DataSyncTableRouteDTO resolveTaskScope(DataSyncTaskDTO task) {
        DataSyncTableRouteDTO route = new DataSyncTableRouteDTO();
        route.setSourceDatabase(task.getSourceDatabase());
        route.setSourceSchema(task.getSourceSchema());
        route.setSourceTable(task.getSourceTable());
        route.setTargetDatabase(task.getTargetDatabase());
        route.setTargetSchema(task.getTargetSchema());
        route.setTargetTable(task.getTargetTable());
        return resolveRouteScope(task.getSourceDataSourceId(), task.getTargetDataSourceId(), route);
    }

    private DataSyncTableRouteDTO resolveRouteScope(DataSyncTaskEntity task, DataSyncTableRouteEntity route) {
        DataSyncTableRouteDTO value = new DataSyncTableRouteDTO();
        value.setSourceDatabase(route.getSourceDatabase());
        value.setSourceSchema(route.getSourceSchema());
        value.setSourceTable(route.getSourceTable());
        value.setTargetDatabase(route.getTargetDatabase());
        value.setTargetSchema(route.getTargetSchema());
        value.setTargetTable(route.getTargetTable());
        return resolveRouteScope(task.getSourceDataSourceId(), task.getTargetDataSourceId(), value);
    }

    private DataSyncTableRouteDTO resolveRouteScope(
            String sourceId, String targetId, DataSyncTableRouteDTO value) {
        DataSourceVO source = dataSourceService.queryDataSource(sourceId);
        DataSourceVO target = dataSourceService.queryDataSource(targetId);
        DataSyncTableRouteDTO resolved = BeanCopyUtils.copy(value, DataSyncTableRouteDTO.class);
        resolved.setSourceDatabase(scopeValue(source.getDatabase(), value.getSourceDatabase()));
        resolved.setSourceSchema(scopeValue(source.getSchema(), value.getSourceSchema()));
        resolved.setTargetDatabase(scopeValue(target.getDatabase(), value.getTargetDatabase()));
        resolved.setTargetSchema(scopeValue(target.getSchema(), value.getTargetSchema()));
        return resolved;
    }

    private boolean executableDefinitionChanged(
            DataSyncTaskEntity entity, DataSyncTaskDTO dto, DataSyncTableRouteDTO resolvedScope) {
        return !Objects.equals(
                        entity.getSourceDataSourceId(),
                        dto.getSourceDataSourceId().trim())
                || !Objects.equals(entity.getSourceDatabase(), resolvedScope.getSourceDatabase())
                || !Objects.equals(entity.getSourceSchema(), resolvedScope.getSourceSchema())
                || !Objects.equals(entity.getSourceTable(), dto.getSourceTable().trim())
                || !Objects.equals(
                        entity.getTargetDataSourceId(),
                        dto.getTargetDataSourceId().trim())
                || !Objects.equals(entity.getTargetDatabase(), resolvedScope.getTargetDatabase())
                || !Objects.equals(entity.getTargetSchema(), resolvedScope.getTargetSchema())
                || !Objects.equals(entity.getTargetTable(), dto.getTargetTable().trim())
                || taskWriteMode(entity) != requireWriteMode(dto.getWriteMode())
                || Boolean.TRUE.equals(entity.getAutoCreateTable())
                || StringUtils.isNotBlank(entity.getMappingConfig())
                || !jsonEquals(
                        normalizedRuntimeConfigJson(entity.getSyncType(), entity.getRuntimeConfig()),
                        runtimeConfigJson(entity.getSyncType(), dto))
                || !jsonEquals(normalizedRetryPolicyJson(entity.getRetryPolicy()), retryPolicyJson(dto));
    }

    private boolean jsonEquals(String left, String right) {
        if (Objects.equals(left, right)) return true;
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) return false;
        return JSONUtils.readTree(left).equals(JSONUtils.readTree(right));
    }

    private void applyDefinition(
            DataSyncTaskEntity entity, DataSyncTaskDTO dto, DataSyncTableRouteDTO resolvedScope) {
        entity.setSourceDataSourceId(dto.getSourceDataSourceId().trim());
        entity.setSourceDatabase(resolvedScope.getSourceDatabase());
        entity.setSourceSchema(resolvedScope.getSourceSchema());
        entity.setSourceTable(dto.getSourceTable().trim());
        entity.setTargetDataSourceId(dto.getTargetDataSourceId().trim());
        entity.setTargetDatabase(resolvedScope.getTargetDatabase());
        entity.setTargetSchema(resolvedScope.getTargetSchema());
        entity.setTargetTable(dto.getTargetTable().trim());
        // Retain V3 persisted columns as read-only tombstones; no new policies are accepted.
        entity.setAutoCreateTable(false);
        entity.setMappingConfig(null);
        entity.setWriteMode(requireWriteMode(dto.getWriteMode()));
        entity.setRuntimeConfig(runtimeConfigJson(entity.getSyncType(), dto));
        entity.setRetryPolicy(retryPolicyJson(dto));
        entity.setRemark(StringUtils.trimToNull(dto.getRemark()));
    }

    private void validateTaskDefinition(
            DataSyncType syncType, DataSyncTaskDTO dto, DataSyncTableRouteDTO resolvedScope) {
        validateWriteMode(syncType, dto.getWriteMode());
        if (syncType == DataSyncType.REALTIME) {
            validateRealtimeDatasourceTypes(dto.getSourceDataSourceId(), dto.getTargetDataSourceId());
        }
        validateRouteTables(
                dto.getSourceDataSourceId(), dto.getTargetDataSourceId(), resolvedScope, syncType, dto.getWriteMode());
    }

    private void validateRealtimeDatasourceTypes(String sourceDataSourceId, String targetDataSourceId) {
        DataSourceVO source = dataSourceService.queryDataSource(sourceDataSourceId);
        DataSourceVO target = dataSourceService.queryDataSource(targetDataSourceId);
        if (!"MYSQL".equals(source.getDbType())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步来源数据源仅支持 MYSQL");
        }
        if (!REALTIME_TARGET_TYPES.contains(target.getDbType())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步目标数据源仅支持 MYSQL / POSTGRE_SQL / ORACLE");
        }
    }

    /**
     * Validates existing same-name source/target tables. Column binding is a Connector
     * responsibility, not a user-editable Mapping definition or an automatic DDL request.
     */
    private void validateRouteTables(
            String sourceDataSourceId,
            String targetDataSourceId,
            DataSyncTableRouteDTO scope,
            DataSyncType syncType,
            DataSyncWriteMode writeMode) {
        DataSourceTablePathDTO sourcePath =
                tablePath(scope.getSourceDatabase(), scope.getSourceSchema(), scope.getSourceTable());
        DataSourceTablePathDTO targetPath =
                tablePath(scope.getTargetDatabase(), scope.getTargetSchema(), scope.getTargetTable());
        dataSourceService.queryCatalogTable(sourceDataSourceId, sourcePath);
        if (dataSourceService.findCatalogTable(targetDataSourceId, targetPath).isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.TARGET_TABLE_NOT_FOUND);
        }

        TableSchema sourceSchema = dataSourceService.queryTableSchema(sourceDataSourceId, sourcePath);
        TableSchema targetSchema = dataSourceService.queryTableSchema(targetDataSourceId, targetPath);
        Map<String, Column> sourceByName = schemaColumns(sourceSchema);
        Map<String, Column> targetByName = schemaColumns(targetSchema);

        for (Column source : sourceSchema.columns()) {
            Column target = targetByName.get(source.name().toLowerCase(Locale.ROOT));
            if (target == null) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "目标表缺少同名字段：" + source.name());
            }
            if (!JdbcSchemaCompatibility.isCompatible(source, target)) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "来源与目标字段类型不兼容：" + source.name());
            }
        }
        for (Column target : targetSchema.columns()) {
            if (!sourceByName.containsKey(target.name().toLowerCase(Locale.ROOT)) && !target.nullable()) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "目标表存在未匹配的必填字段：" + target.name());
            }
        }
        if (syncType == DataSyncType.REALTIME || writeMode == DataSyncWriteMode.UPSERT) {
            Set<String> sourceKeys = normalizedKeys(sourceSchema.primaryKeys());
            Set<String> targetKeys = normalizedKeys(targetSchema.primaryKeys());
            if (sourceKeys.isEmpty() || !sourceKeys.equals(targetKeys)) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "更新写入要求来源与目标表主键同名且完整");
            }
        }
    }

    private Map<String, Column> schemaColumns(TableSchema schema) {
        Map<String, Column> columns = new LinkedHashMap<>();
        for (Column column : schema.columns()) {
            Column previous = columns.putIfAbsent(column.name().toLowerCase(Locale.ROOT), column);
            if (previous != null) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "表结构存在大小写不敏感的重名字段");
            }
        }
        return columns;
    }

    private Set<String> normalizedKeys(List<String> keys) {
        Set<String> normalized = new HashSet<>();
        for (String key : keys) {
            normalized.add(key.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private void materializeCreatePolicies(DataSyncType syncType, DataSyncTaskDTO dto) {
        if (dto.getRetryPolicy() == null) {
            dto.setRetryPolicy(smartRetryPolicy());
        } else {
            normalizeRetryPolicy(dto.getRetryPolicy());
        }
        if (syncType == DataSyncType.REALTIME) {
            if (dto.getRealtimeConfig() == null) {
                dto.setRealtimeConfig(new DataSyncRealtimeConfigDTO());
            }
            return;
        }

        if (dto.getRuntimeConfig() == null) {
            DataSyncRuntimeConfigDTO runtimeConfig = new DataSyncRuntimeConfigDTO();
            runtimeConfig.setPolicy(DataSyncRuntimePolicy.AUTO);
            dto.setRuntimeConfig(runtimeConfig);
            return;
        }
        normalizeRuntimePolicy(dto.getRuntimeConfig());
    }

    private void materializeUpdatePolicies(DataSyncTaskEntity entity, DataSyncTaskDTO dto) {
        if (dto.getRetryPolicy() == null) {
            dto.setRetryPolicy(retryPolicyConfig(entity.getRetryPolicy()));
        } else {
            normalizeRetryPolicy(dto.getRetryPolicy());
        }
        if (entity.getSyncType() == DataSyncType.REALTIME) {
            if (dto.getRealtimeConfig() == null) {
                dto.setRealtimeConfig(realtimeConfig(entity.getRuntimeConfig()));
            }
            return;
        }
        if (dto.getRuntimeConfig() == null) {
            dto.setRuntimeConfig(runtimeConfig(entity.getRuntimeConfig()));
        } else {
            normalizeRuntimePolicy(dto.getRuntimeConfig());
        }
    }

    private DataSyncRuntimeConfigDTO runtimeConfig(String json) {
        DataSyncRuntimeConfigDTO config = StringUtils.isBlank(json)
                ? new DataSyncRuntimeConfigDTO()
                : JSONUtils.parseObject(json, DataSyncRuntimeConfigDTO.class);
        normalizeRuntimePolicy(config);
        return config;
    }

    private void normalizeRuntimePolicy(DataSyncRuntimeConfigDTO config) {
        if (config.getPolicy() == null) {
            config.setPolicy(DataSyncRuntimePolicy.FIXED);
        }
    }

    private DataSyncRealtimeConfigDTO realtimeConfig(String json) {
        return StringUtils.isBlank(json)
                ? new DataSyncRealtimeConfigDTO()
                : JSONUtils.parseObject(json, DataSyncRealtimeConfigDTO.class);
    }

    private DataSyncRetryPolicyDTO retryPolicyConfig(String json) {
        DataSyncRetryPolicyDTO policy = StringUtils.isBlank(json)
                ? new DataSyncRetryPolicyDTO()
                : JSONUtils.parseObject(json, DataSyncRetryPolicyDTO.class);
        normalizeRetryPolicy(policy);
        return policy;
    }

    private DataSyncRetryPolicyDTO smartRetryPolicy() {
        DataSyncRetryPolicyDTO policy = new DataSyncRetryPolicyDTO();
        policy.setMode(DataSyncRetryPolicyMode.SMART);
        policy.setMaxAttempts(3);
        policy.setBackoffSeconds(15);
        return policy;
    }

    private void normalizeRetryPolicy(DataSyncRetryPolicyDTO policy) {
        if (policy.getMode() == null) {
            policy.setMode(DataSyncRetryPolicyMode.FIXED);
        }
    }

    private String runtimeConfigJson(DataSyncType syncType, DataSyncTaskDTO dto) {
        return syncType == DataSyncType.REALTIME
                ? JSONUtils.toJson(dto.getRealtimeConfig())
                : JSONUtils.toJson(dto.getRuntimeConfig());
    }

    private String normalizedRuntimeConfigJson(DataSyncType syncType, String json) {
        if (syncType == DataSyncType.REALTIME) {
            DataSyncRealtimeConfigDTO config = StringUtils.isBlank(json)
                    ? new DataSyncRealtimeConfigDTO()
                    : JSONUtils.parseObject(json, DataSyncRealtimeConfigDTO.class);
            return JSONUtils.toJson(config);
        }
        return JSONUtils.toJson(runtimeConfig(json));
    }

    private String retryPolicyJson(DataSyncTaskDTO dto) {
        DataSyncRetryPolicyDTO policy = dto.getRetryPolicy() == null ? smartRetryPolicy() : dto.getRetryPolicy();
        normalizeRetryPolicy(policy);
        return JSONUtils.toJson(policy);
    }

    private String normalizedRetryPolicyJson(String json) {
        return JSONUtils.toJson(retryPolicyConfig(json));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private DataSourceTablePathDTO tablePath(String database, String schema, String table) {
        String tableName = StringUtils.trimToNull(table);
        if (tableName == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "表名称不能为空");

        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(StringUtils.trimToNull(database));
        path.setSchema(StringUtils.trimToNull(schema));
        path.setTable(tableName);
        return path;
    }

    private String scopeValue(String boundValue, String requestedValue) {
        String bound = StringUtils.trimToNull(boundValue);
        return bound != null ? bound : StringUtils.trimToNull(requestedValue);
    }

    private DataSyncType requireSyncType(DataSyncType syncType) {
        if (syncType == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "同步类型不能为空");
        }
        return syncType;
    }

    private DataSyncWriteMode requireWriteMode(DataSyncWriteMode writeMode) {
        if (writeMode == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "写入方式不能为空");
        }
        return writeMode;
    }

    private void validateWriteMode(DataSyncType syncType, DataSyncWriteMode writeMode) {
        DataSyncWriteMode resolved = requireWriteMode(writeMode);
        if (syncType == DataSyncType.REALTIME && resolved != DataSyncWriteMode.APPEND) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "REALTIME 当前固定使用 APPEND 写入方式");
        }
    }

    private DataSyncWriteMode taskWriteMode(DataSyncTaskEntity task) {
        return task.getWriteMode() == null ? DataSyncWriteMode.APPEND : task.getWriteMode();
    }

    private DataSyncTaskStatus taskStatus(DataSyncTaskEntity task) {
        return task.getStatus() == null ? DataSyncTaskStatus.PUBLISHED : task.getStatus();
    }

    private DataSyncDesiredState taskDesiredState(DataSyncTaskEntity task) {
        return task.getDesiredState() == null ? DataSyncDesiredState.STOPPED : task.getDesiredState();
    }

    private void updateDesiredState(String workspaceId, DataSyncTaskEntity task, DataSyncDesiredState desiredState) {
        if (taskDesiredState(task) == desiredState) return;
        task.setDesiredState(desiredState);
        task.initUpdate();
        if (taskRepository.update(workspaceId, task) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "更新实时同步期望状态失败");
        }
    }

    private void requireTaskStatus(DataSyncTaskEntity task, DataSyncTaskStatus expected, String detail) {
        if (taskStatus(task) != expected) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK_STATUS, detail);
        }
    }

    private DataSyncTaskEntity requireTask(String workspaceId, String id) {
        if (StringUtils.isBlank(id)) throw new DataSyncException(DataSyncErrorCode.TASK_NOT_FOUND);
        return taskRepository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.TASK_NOT_FOUND));
    }

    private DataSyncInstanceEntity requireInstance(String workspaceId, String id) {
        if (StringUtils.isBlank(id)) throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_FOUND);
        return instanceRepository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_FOUND));
    }

    private void ensureTaskNameAvailable(String workspaceId, String name, String excludeId) {
        if (taskRepository.existsByName(workspaceId, name, excludeId)) {
            throw new DataSyncException(DataSyncErrorCode.DUPLICATE_TASK_NAME);
        }
    }

    /**
     * 将显式 Table Route 请求转换为服务端解析后的物理路径，验证每张表的 Mapping / Schema。
     * Source / Target Datasource 始终由根 Task 共享；null 继续走单表兼容写路径。
     */
    private List<DataSyncTableRouteDTO> prepareExplicitTableRoutes(DataSyncTaskDTO task) {
        List<DataSyncTableRouteDTO> requested = task.getTableRoutes();
        if (requested == null) return null;
        if (task.getSyncType() != DataSyncType.OFFLINE) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "REALTIME 当前只支持单表定义");
        }
        if (requested.isEmpty() || requested.size() > 50) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "请选择 1 到 50 张来源表");
        }

        List<DataSyncTableRouteDTO> normalized = new ArrayList<>(requested.size());
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (DataSyncTableRouteDTO raw : requested) {
            if (raw == null) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "表级 Route 不能为空");
            }
            DataSyncTableRouteDTO route = BeanCopyUtils.copy(raw, DataSyncTableRouteDTO.class);
            route.setId(StringUtils.trimToNull(route.getId()));
            String sourceTable = StringUtils.trimToNull(route.getSourceTable());
            String targetTable = StringUtils.trimToNull(route.getTargetTable());
            if (sourceTable == null || targetTable == null
                    || sourceTable.length() > 128 || targetTable.length() > 128) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "来源表或目标表名称不合法");
            }
            route.setSourceTable(sourceTable);
            route.setTargetTable(targetTable);
            DataSyncTableRouteDTO scope =
                    resolveRouteScope(task.getSourceDataSourceId(), task.getTargetDataSourceId(), route);
            String sourceKey = tableIdentity(scope.getSourceDatabase(), scope.getSourceSchema(), sourceTable);
            String targetKey = tableIdentity(scope.getTargetDatabase(), scope.getTargetSchema(), targetTable);
            if (!sources.add(sourceKey) || !targets.add(targetKey)) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "同一任务的来源表或目标表不能重复");
            }
            validateRouteTables(
                    task.getSourceDataSourceId(), task.getTargetDataSourceId(), scope, task.getSyncType(), task.getWriteMode());
            normalized.add(scope);
        }

        // The first-Route projection remains until the Task/Route schema cleanup.
        DataSyncTableRouteDTO first = normalized.getFirst();
        task.setSourceDatabase(first.getSourceDatabase());
        task.setSourceSchema(first.getSourceSchema());
        task.setSourceTable(first.getSourceTable());
        task.setTargetDatabase(first.getTargetDatabase());
        task.setTargetSchema(first.getTargetSchema());
        task.setTargetTable(first.getTargetTable());
        return List.copyOf(normalized);
    }

    private String tableIdentity(String database, String schema, String table) {
        return ((database == null ? "" : database) + "\\u0000" + (schema == null ? "" : schema) + "\\u0000" + table)
                .toLowerCase(Locale.ROOT);
    }

    private void createCompatibilityTableRoute(DataSyncTaskEntity task, String operatorUserId) {
        DataSyncTableRouteEntity route = new DataSyncTableRouteEntity();
        applyCompatibilityTableRoute(route, task);
        route.initCreate(operatorUserId);
        if (tableRouteRepository.add(route) == null) {
            throw new DataSyncException(DataSyncErrorCode.CREATE_TASK_FAILED, "表级 Route 创建失败");
        }
    }

    private void synchronizeCompatibilityTableRoute(DataSyncTaskEntity task, String operatorUserId) {
        List<DataSyncTableRouteEntity> routes = tableRouteRepository.queryByTask(task.getWorkspaceId(), task.getId());
        if (routes.size() > 1) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "多表任务暂不支持通过单表兼容接口编辑");
        }
        if (routes.isEmpty()) {
            createCompatibilityTableRoute(task, operatorUserId);
            return;
        }

        DataSyncTableRouteEntity route = routes.get(0);
        if (compatibilityTableRouteMatches(route, task)) return;
        applyCompatibilityTableRoute(route, task);
        route.initUpdate(operatorUserId);
        if (tableRouteRepository.update(task.getWorkspaceId(), route) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "表级 Route 更新失败");
        }
    }

    private void applyCompatibilityTableRoute(DataSyncTableRouteEntity route, DataSyncTaskEntity task) {
        route.setWorkspaceId(task.getWorkspaceId());
        route.setTaskId(task.getId());
        route.setSourceDatabase(task.getSourceDatabase());
        route.setSourceSchema(task.getSourceSchema());
        route.setSourceTable(task.getSourceTable());
        route.setTargetDatabase(task.getTargetDatabase());
        route.setTargetSchema(task.getTargetSchema());
        route.setTargetTable(task.getTargetTable());
        route.setAutoCreateTable(false);
        route.setMappingConfig(null);
        route.setSortOrder(0);
    }

    private boolean compatibilityTableRouteMatches(DataSyncTableRouteEntity route, DataSyncTaskEntity task) {
        return Objects.equals(route.getWorkspaceId(), task.getWorkspaceId())
                && Objects.equals(route.getTaskId(), task.getId())
                && Objects.equals(route.getSourceDatabase(), task.getSourceDatabase())
                && Objects.equals(route.getSourceSchema(), task.getSourceSchema())
                && Objects.equals(route.getSourceTable(), task.getSourceTable())
                && Objects.equals(route.getTargetDatabase(), task.getTargetDatabase())
                && Objects.equals(route.getTargetSchema(), task.getTargetSchema())
                && Objects.equals(route.getTargetTable(), task.getTargetTable())
                && !Boolean.TRUE.equals(route.getAutoCreateTable())
                && StringUtils.isBlank(route.getMappingConfig())
                && Objects.equals(route.getSortOrder(), 0);
    }

    private DataSyncTaskVO toTaskListVO(DataSyncTaskEntity source, DataSyncScheduleEntity schedule) {
        DataSyncTaskVO target = toTaskVO(source, false);
        if (schedule != null) {
            target.setScheduleCronExpression(schedule.getCronExpression());
            target.setScheduleTimeZone(schedule.getTimeZone());
            target.setScheduleEnabled(Boolean.TRUE.equals(schedule.getEnabled()));
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

    private DataSyncTaskVO toTaskVO(DataSyncTaskEntity source) {
        return toTaskVO(source, true);
    }

    private DataSyncTaskVO toTaskVO(DataSyncTaskEntity source, boolean includeTableRoutes) {
        DataSyncTaskVO target = BeanCopyUtils.copy(
                source,
                DataSyncTaskVO.class,
                "syncType",
                "status",
                "desiredState",
                "writeMode",
                "runtimeConfig",
                "retryPolicy");
        target.setSyncType(
                source.getSyncType() == null ? null : source.getSyncType().name());
        target.setStatus(taskStatus(source).name());
        target.setDesiredState(taskDesiredState(source).name());
        target.setWriteMode(taskWriteMode(source).name());
        if (includeTableRoutes) {
            target.setTableRoutes(toTableRouteVOs(source));
        }
        target.setRetryPolicy(toRetryPolicyVO(source.getRetryPolicy()));
        if (source.getSyncType() == DataSyncType.REALTIME) {
            target.setRealtimeConfig(toRealtimeConfigVO(source.getRuntimeConfig()));
        } else {
            target.setRuntimeConfig(toRuntimeConfigVO(source.getRuntimeConfig()));
        }
        return target;
    }

    private List<DataSyncTableRouteVO> toTableRouteVOs(DataSyncTaskEntity task) {
        List<DataSyncTableRouteEntity> routes = tableRouteRepository.queryByTask(task.getWorkspaceId(), task.getId());
        return routes.stream().map(this::toTableRouteVO).toList();
    }

    private DataSyncTableRouteVO toTableRouteVO(DataSyncTableRouteEntity source) {
        return BeanCopyUtils.copy(source, DataSyncTableRouteVO.class);
    }

    private DataSyncRuntimeConfigVO toRuntimeConfigVO(String json) {
        return BeanCopyUtils.copy(runtimeConfig(json), DataSyncRuntimeConfigVO.class);
    }

    private DataSyncRealtimeConfigVO toRealtimeConfigVO(String json) {
        return BeanCopyUtils.copy(realtimeConfig(json), DataSyncRealtimeConfigVO.class);
    }

    private DataSyncRetryPolicyVO toRetryPolicyVO(String json) {
        return BeanCopyUtils.copy(retryPolicyConfig(json), DataSyncRetryPolicyVO.class);
    }

    private DataSyncAttemptVO toAttemptVO(DataSyncAttemptEntity source) {
        DataSyncAttemptVO target = BeanCopyUtils.copy(source, DataSyncAttemptVO.class, "status");
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        return target;
    }

    private DataSyncExecutionEventVO toExecutionEventVO(DataSyncExecutionEventEntity source) {
        DataSyncExecutionEventVO target =
                BeanCopyUtils.copy(source, DataSyncExecutionEventVO.class, "level", "eventType");
        target.setLevel(source.getLevel() == null ? null : source.getLevel().name());
        target.setEventType(
                source.getEventType() == null ? null : source.getEventType().name());
        return target;
    }

    private DataSyncTableAttemptVO toTableAttemptVO(DataSyncTableAttemptEntity source) {
        DataSyncTableAttemptVO target = BeanCopyUtils.copy(source, DataSyncTableAttemptVO.class, "status");
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        return target;
    }

    private DataSyncTableExecutionVO toTableExecutionVO(DataSyncTableExecutionEntity source) {
        DataSyncTableExecutionVO target = BeanCopyUtils.copy(source, DataSyncTableExecutionVO.class, "status");
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        return target;
    }

    private DataSyncInstanceVO toInstanceVO(DataSyncInstanceEntity source, boolean includeSnapshot) {
        DataSyncInstanceVO target = BeanCopyUtils.copy(
                source, DataSyncInstanceVO.class, "syncType", "triggerType", "status", "definitionSnapshot");
        target.setSyncType(
                source.getSyncType() == null ? null : source.getSyncType().name());
        target.setTriggerType(
                source.getTriggerType() == null ? null : source.getTriggerType().name());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        if (includeSnapshot && StringUtils.isNotBlank(source.getDefinitionSnapshot())) {
            DataSyncDefinitionSnapshotVO snapshot =
                    JSONUtils.parseObject(source.getDefinitionSnapshot(), DataSyncDefinitionSnapshotVO.class);
            target.setDefinitionSnapshot(snapshot);
            if (snapshot.getTableRoutes() != null && snapshot.getTableRoutes().size() > 1) {
                target.setTableExecutions(
                        tableExecutionRepository.queryByExecution(source.getWorkspaceId(), source.getId()).stream()
                                .map(this::toTableExecutionVO)
                                .toList());
            }
        }
        return target;
    }
    /** 不创建缺少执行引擎支撑的实例，保证任务/调度/历史查询契约仍可用。 */
    private DataSyncException runtimeUnavailable() {
        return new DataSyncException(DataSyncErrorCode.EXECUTION_FAILED, "同步引擎尚未接入，当前无法运行同步任务");
    }
}
