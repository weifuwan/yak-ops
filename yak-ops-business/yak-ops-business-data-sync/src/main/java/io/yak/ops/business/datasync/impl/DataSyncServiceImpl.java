package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.business.datasync.catalog.DataSyncCatalogColumns;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncTableAttemptLifecycle;
import io.yak.ops.business.datasync.execution.planning.OfflineRuntimePlan;
import io.yak.ops.business.datasync.execution.planning.OfflineRuntimePlanner;
import io.yak.ops.business.datasync.execution.trace.ExecutionTracePage;
import io.yak.ops.business.datasync.execution.trace.ExecutionTraceRecord;
import io.yak.ops.business.datasync.execution.trace.ExecutionTraceSide;
import io.yak.ops.business.datasync.execution.trace.ExecutionTraceStore;
import io.yak.ops.business.datasync.execution.trace.ExecutionTraceSummarySnapshot;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleDefinition;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFire;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFireListener;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.business.datasync.scheduler.ScheduleEngineException;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.business.datasync.schema.catalog.LogicalTableNormalizer;
import io.yak.ops.business.datasync.schema.catalog.SourceTableIntrospector;
import io.yak.ops.business.datasync.schema.mapping.ResolvedSchemaMapping;
import io.yak.ops.business.datasync.schema.mapping.SchemaColumnMapping;
import io.yak.ops.business.datasync.schema.mapping.SchemaMappingResolver;
import io.yak.ops.business.datasync.schema.target.TargetColumnPlan;
import io.yak.ops.business.datasync.schema.target.TargetSchemaCompatibility;
import io.yak.ops.business.datasync.schema.target.TargetSchemaCompatibilityResult;
import io.yak.ops.business.datasync.schema.target.TargetTablePlan;
import io.yak.ops.business.datasync.schema.target.TargetTablePlanner;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncColumnMappingDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingPreviewDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRealtimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRetryPolicyDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRuntimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTableRouteDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncColumnMappingVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncFieldMappingVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingPreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingVO;
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
import io.yak.ops.common.bean.vo.datasync.DataSyncTableRouteSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableRouteVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTracePageVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTraceSummaryVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncOperationsRange;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
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
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaCompatibility;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaMapper;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import jakarta.annotation.Resource;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    private final SchemaMappingResolver schemaMappingResolver = new SchemaMappingResolver();
    private final OfflineRuntimePlanner offlineRuntimePlanner = new OfflineRuntimePlanner();

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
    private SourceTableIntrospector sourceTableIntrospector;

    @Resource
    private TargetTablePlanner targetTablePlanner;

    @Resource
    private DataSyncAttemptLifecycle attemptLifecycle;

    @Resource
    private DataSyncTableAttemptLifecycle tableAttemptLifecycle;

    @Resource
    private ExecutionTraceStore executionTraceStore;

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
        DataSyncMappingPreviewDTO resolvedScope =
                resolveMappingScope(BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class));
        validateTaskDefinition(syncType, dto, resolvedScope);
        requireCompatibleMapping(resolvedScope);

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
        DataSyncMappingPreviewDTO resolvedScope =
                resolveMappingScope(BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class));
        validateTaskDefinition(syncType, dto, resolvedScope);
        requireCompatibleMapping(resolvedScope);

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
    public DataSyncMappingPreviewVO previewMapping(DataSyncMappingPreviewDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "字段映射参数不完整");
        dto.setMapping(normalizeMapping(dto.getMapping()));
        return previewResolvedMapping(resolveMappingScope(dto));
    }

    private DataSyncMappingPreviewVO previewResolvedMapping(DataSyncMappingPreviewDTO dto) {
        DataSourceTablePathDTO sourcePath =
                tablePath(dto.getSourceDatabase(), dto.getSourceSchema(), dto.getSourceTable());
        DataSourceTablePathDTO targetPath =
                tablePath(dto.getTargetDatabase(), dto.getTargetSchema(), dto.getTargetTable());
        List<DataSourceCatalogColumnVO> sourceColumns =
                dataSourceService.queryCatalogColumns(dto.getSourceDataSourceId(), sourcePath);
        LogicalTable sourceLogicalTable = LogicalTableNormalizer.fromCatalog(
                dataSourceService.queryCatalogTable(dto.getSourceDataSourceId(), sourcePath), sourceColumns);
        ResolvedSchemaMapping resolvedMapping = resolveSchemaMapping(sourceLogicalTable, dto.getMapping());
        Map<String, DataSourceCatalogColumnVO> sourceByName = DataSyncCatalogColumns.indexByName(sourceColumns);

        boolean autoCreateTable = Boolean.TRUE.equals(dto.getAutoCreateTable());
        Optional<DataSourceCatalogTableVO> targetTable =
                dataSourceService.findCatalogTable(dto.getTargetDataSourceId(), targetPath);
        if (targetTable.isEmpty()) {
            if (autoCreateTable) {
                return previewAutoCreateMapping(dto, sourceByName, resolvedMapping);
            }
            DataSyncMappingPreviewVO result = new DataSyncMappingPreviewVO();
            result.setTargetTableExists(false);
            result.setAutoCreateTable(false);
            result.setMappings(resolvedMapping.columns().stream()
                    .map(mapping -> toFieldMapping(
                            mapping, DataSyncCatalogColumns.findByName(sourceByName, mapping.source()), null))
                    .toList());
            result.setCompatible(false);
            return result;
        }

        List<DataSourceCatalogColumnVO> targetColumns =
                dataSourceService.queryCatalogColumns(dto.getTargetDataSourceId(), targetPath);
        Map<String, DataSourceCatalogColumnVO> targetByName = DataSyncCatalogColumns.indexByName(targetColumns);
        TargetSchemaCompatibilityResult compatibility =
                TargetSchemaCompatibility.check(resolvedMapping.targetTable(), targetColumns);

        DataSyncMappingPreviewVO result = new DataSyncMappingPreviewVO();
        result.setTargetTableExists(true);
        result.setAutoCreateTable(autoCreateTable);
        result.setUnsupportedReasons(compatibility.issues());
        result.setMappings(resolvedMapping.columns().stream()
                .map(mapping -> toFieldMapping(
                        mapping,
                        DataSyncCatalogColumns.findByName(sourceByName, mapping.source()),
                        DataSyncCatalogColumns.findByName(targetByName, mapping.target())))
                .toList());
        result.setCompatible(!result.getMappings().isEmpty()
                && result.getMappings().stream().allMatch(DataSyncFieldMappingVO::isCompatible)
                && compatibility.compatible());
        return result;
    }

    private DataSyncMappingPreviewVO previewAutoCreateMapping(
            DataSyncMappingPreviewDTO dto,
            Map<String, DataSourceCatalogColumnVO> sourceByName,
            ResolvedSchemaMapping resolvedMapping) {
        DataSourceVO targetDataSource = dataSourceService.queryDataSource(dto.getTargetDataSourceId());
        TargetTablePlan plan = targetTablePlanner.plan(
                resolvedMapping.targetTable(),
                targetDataSource.getDbType(),
                dto.getTargetDatabase(),
                dto.getTargetSchema(),
                dto.getTargetTable());

        DataSyncMappingPreviewVO result = new DataSyncMappingPreviewVO();
        result.setTargetTableExists(false);
        result.setAutoCreateTable(true);
        result.setCreateTableSql(plan.createTableSql());
        result.setDdlStatements(plan.ddlStatements());
        result.setWarnings(plan.warnings());
        result.setUnsupportedReasons(plan.unsupportedReasons());
        result.setMappings(resolvedMapping.columns().stream()
                .map(mapping -> toAutoCreateFieldMapping(
                        mapping,
                        DataSyncCatalogColumns.findByName(sourceByName, mapping.source()),
                        findPlanColumn(plan, mapping.target())))
                .toList());
        result.setCompatible(!result.getMappings().isEmpty() && plan.supported());
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
        throw new DataSyncException(DataSyncErrorCode.ENGINE_UNAVAILABLE);
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
                // 引擎缺席时保留已配置状态，但不注册实际 Cron 触发器。
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
        requireSchedule(workspaceId, taskId);
        throw new DataSyncException(DataSyncErrorCode.ENGINE_UNAVAILABLE);
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
        // 已有 Cron 配置继续保留；缺少同步引擎时不注册任何触发器。
        LOG.info("数据同步引擎暂不可用，跳过 Cron 调度恢复");
    }

    @Override
    public void restoreRealtimeDesiredState() {
        // 保留 Desired State 与历史实例，不在缺少 Connector 时启动空转 Execution。
        LOG.info("数据同步引擎暂不可用，跳过实时任务自动恢复");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void onFire(DataSyncScheduleFire fire) {
        // 防御历史 Cron 触发器，禁止创建无法执行的 PENDING 实例。
        LOG.warn("数据同步引擎暂不可用，忽略调度触发：scheduleId={}", fire == null ? null : fire.scheduleId());
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
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncInstanceEntity instance = requireOfflineTraceInstance(workspaceId, instanceId);
        int resolvedAttemptNo = resolveTraceAttemptNo(instance, attemptNo);
        return toTraceSummaryVO(executionTraceStore.querySummary(workspaceId, instanceId, resolvedAttemptNo));
    }

    @Override
    public DataSyncTracePageVO<DataSyncSourceTraceVO> queryExecutionSourceTrace(
            String instanceId, Integer attemptNo, Integer pageSize, String cursor, String status) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncInstanceEntity instance = requireOfflineTraceInstance(workspaceId, instanceId);
        int resolvedAttemptNo = resolveTraceAttemptNo(instance, attemptNo);
        ExecutionTracePage page = queryTracePage(
                workspaceId, instanceId, resolvedAttemptNo, ExecutionTraceSide.SOURCE, pageSize, cursor, status);
        return toSourceTracePageVO(page);
    }

    @Override
    public DataSyncTracePageVO<DataSyncSinkTraceVO> queryExecutionSinkTrace(
            String instanceId, Integer attemptNo, Integer pageSize, String cursor, String status) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncInstanceEntity instance = requireOfflineTraceInstance(workspaceId, instanceId);
        int resolvedAttemptNo = resolveTraceAttemptNo(instance, attemptNo);
        ExecutionTracePage page = queryTracePage(
                workspaceId, instanceId, resolvedAttemptNo, ExecutionTraceSide.SINK, pageSize, cursor, status);
        return toSinkTracePageVO(page);
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
        if (instance.getStatus() == null || instance.getStatus().isTerminal()) {
            return toInstanceVO(instance, true);
        }

        boolean multiTable = isMultiTableExecution(instance);
        if (instance.getSyncType() == DataSyncType.REALTIME) {
            DataSyncTaskEntity task = taskRepository.queryById(workspaceId, instance.getTaskId()).orElse(null);
            if (task != null) {
                updateDesiredState(workspaceId, task, DataSyncDesiredState.STOPPED);
            }
        }

        if (instance.getStatus() == DataSyncInstanceStatus.PENDING
                || instance.getStatus() == DataSyncInstanceStatus.RETRY_WAITING) {
            if (!instanceRepository.cancelExecution(workspaceId, id, instance.getStatus(), DateUtils.now())) {
                throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
            }
            if (multiTable) {
                tableAttemptLifecycle.cancelUnfinished(workspaceId, id, DataSyncTableExecutionStatus.CANCELED);
            } else {
                attemptLifecycle.cancelActiveAttempt(workspaceId, id);
            }
            attemptLifecycle.recordExecutionCanceled(workspaceId, id);
        } else if (instance.getStatus() == DataSyncInstanceStatus.RUNNING) {
            // 原 Runtime 已移除，不能声称停止了一个不存在的进程内运行句柄。
            if (!instanceRepository.transitionStatus(
                    workspaceId, id, DataSyncInstanceStatus.RUNNING, DataSyncInstanceStatus.LOST,
                    null, DateUtils.now(),
                    DataSyncErrorCode.EXECUTION_LOST.getCode(),
                    DataSyncErrorCode.EXECUTION_LOST.getMessage())) {
                throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
            }
            if (multiTable) {
                tableAttemptLifecycle.cancelUnfinished(workspaceId, id, DataSyncTableExecutionStatus.LOST);
            }
            attemptLifecycle.recordExecutionLost(workspaceId, id, "原同步引擎已移除，运行实例标记为 LOST");
        } else {
            throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
        }

        return toInstanceVO(requireInstance(workspaceId, id), true);
    }

    private boolean isMultiTableExecution(DataSyncInstanceEntity instance) {
        if (instance.getSyncType() != DataSyncType.OFFLINE || StringUtils.isBlank(instance.getDefinitionSnapshot()))
            return false;
        DataSyncDefinitionSnapshotVO snapshot =
                JSONUtils.parseObject(instance.getDefinitionSnapshot(), DataSyncDefinitionSnapshotVO.class);
        return snapshot.getTableRoutes() != null && snapshot.getTableRoutes().size() > 1;
    }

    private DataSyncInstanceEntity requireOfflineTraceInstance(String workspaceId, String instanceId) {
        DataSyncInstanceEntity instance = requireInstance(workspaceId, instanceId);
        if (instance.getSyncType() != DataSyncType.OFFLINE) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "Runtime Trace 当前仅支持离线同步");
        }
        return instance;
    }

    private int resolveTraceAttemptNo(DataSyncInstanceEntity instance, Integer attemptNo) {
        int currentAttempt = instance.getCurrentAttempt() == null ? 1 : Math.max(1, instance.getCurrentAttempt());
        int resolved = attemptNo == null ? currentAttempt : attemptNo;
        if (resolved <= 0 || resolved > currentAttempt) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "Attempt 序号不合法");
        }
        return resolved;
    }

    private ExecutionTracePage queryTracePage(
            String workspaceId,
            String instanceId,
            int attemptNo,
            ExecutionTraceSide side,
            Integer pageSize,
            String cursor,
            String status) {
        int resolvedPageSize = pageSize == null ? DEFAULT_TRACE_PAGE_SIZE : pageSize;
        try {
            return executionTraceStore.queryPage(
                    workspaceId,
                    instanceId,
                    attemptNo,
                    side,
                    resolvedPageSize,
                    StringUtils.trimToNull(cursor),
                    StringUtils.trimToNull(status));
        } catch (IllegalArgumentException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, exception.getMessage(), exception);
        }
    }

    private DataSyncTraceSummaryVO toTraceSummaryVO(ExecutionTraceSummarySnapshot source) {
        DataSyncTraceSummaryVO target = new DataSyncTraceSummaryVO();
        target.setAttemptNo(source.attemptNo());
        target.setAvailable(source.available());
        target.setComplete(source.complete());
        target.setSourceSplitCount(source.sourceSplitCount());
        target.setSourceFinishedSplitCount(source.sourceFinishedSplitCount());
        target.setSourceFailedSplitCount(source.sourceFailedSplitCount());
        target.setSourceRows(source.sourceRows());
        target.setSourceSplitDurationMillis(source.sourceSplitDurationMillis());
        target.setSinkSql(source.sinkSql());
        target.setSinkBatchSize(source.sinkBatchSize());
        target.setSinkSaveMode(source.sinkSaveMode());
        target.setSinkWriteMode(source.sinkWriteMode());
        target.setSinkCommittedBatchCount(source.sinkCommittedBatchCount());
        target.setSinkFailedBatchCount(source.sinkFailedBatchCount());
        target.setSinkRows(source.sinkRows());
        target.setSinkExecuteDurationMillis(source.sinkExecuteDurationMillis());
        target.setSinkCommitDurationMillis(source.sinkCommitDurationMillis());
        target.setErrorCount(source.errorCount());
        target.setDroppedEventCount(source.droppedEventCount());
        return target;
    }

    private DataSyncTracePageVO<DataSyncSourceTraceVO> toSourceTracePageVO(ExecutionTracePage source) {
        DataSyncTracePageVO<DataSyncSourceTraceVO> target = new DataSyncTracePageVO<>();
        target.setRecords(source.records().stream().map(this::toSourceTraceVO).toList());
        target.setNextCursor(source.nextCursor());
        target.setHasMore(source.hasMore());
        return target;
    }

    private DataSyncTracePageVO<DataSyncSinkTraceVO> toSinkTracePageVO(ExecutionTracePage source) {
        DataSyncTracePageVO<DataSyncSinkTraceVO> target = new DataSyncTracePageVO<>();
        target.setRecords(source.records().stream().map(this::toSinkTraceVO).toList());
        target.setNextCursor(source.nextCursor());
        target.setHasMore(source.hasMore());
        return target;
    }

    private DataSyncSourceTraceVO toSourceTraceVO(ExecutionTraceRecord source) {
        DataSyncSourceTraceVO target = new DataSyncSourceTraceVO();
        target.setTimestamp(source.timestamp());
        target.setSplitId(source.splitId());
        target.setWorkerName(source.workerName());
        target.setSql(source.sql());
        target.setParameters(source.parameters());
        target.setSplitColumn(source.splitColumn());
        target.setLowerBoundInclusive(source.lowerBoundInclusive());
        target.setUpperBoundInclusive(source.upperBoundInclusive());
        target.setRows(source.rows());
        target.setDurationMillis(source.durationMillis());
        target.setStatus(source.type().endsWith("_FAILED") ? "FAILED" : "SUCCESS");
        target.setFailureStage(source.failureStage());
        target.setErrorType(source.errorType());
        target.setErrorMessage(source.errorMessage());
        return target;
    }

    private DataSyncSinkTraceVO toSinkTraceVO(ExecutionTraceRecord source) {
        DataSyncSinkTraceVO target = new DataSyncSinkTraceVO();
        target.setTimestamp(source.timestamp());
        target.setBatchNo(source.batchNo());
        target.setRows(source.rows());
        target.setExecuteDurationMillis(source.executeDurationMillis());
        target.setCommitDurationMillis(source.commitDurationMillis());
        target.setStatus(source.type().endsWith("_FAILED") ? "FAILED" : "SUCCESS");
        target.setFailureStage(source.failureStage());
        target.setErrorType(source.errorType());
        target.setErrorMessage(source.errorMessage());
        return target;
    }

    private DataSyncInstanceVO createInstance(
            String workspaceId,
            DataSyncTaskEntity task,
            List<DataSyncTableRouteEntity> routes,
            DataSyncTriggerType triggerType) {
        // 守卫后续调用方：引擎重接入前不创建无法执行的实例。
        throw new DataSyncException(DataSyncErrorCode.ENGINE_UNAVAILABLE);
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
        DataSyncWriteMode writeMode = taskWriteMode(task);
        validateWriteMode(task.getSyncType(), writeMode);
        if (routes.size() > 1 && task.getSyncType() == DataSyncType.REALTIME) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "REALTIME 当前只支持单 Route");
        }

        for (DataSyncTableRouteEntity route : routes) {
            DataSyncMappingPreviewDTO resolvedScope = resolveRouteScope(task, route);
            if (task.getSyncType() == DataSyncType.REALTIME) {
                validateRealtimeTopology(task.getSourceDataSourceId(), task.getTargetDataSourceId(), resolvedScope);
            } else {
                validateOfflineUpsertTarget(
                        task.getSourceDataSourceId(), task.getTargetDataSourceId(), resolvedScope, writeMode);
            }
            requireCompatibleMapping(resolvedScope);
        }
        return routes;
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

    private DataSyncMappingPreviewDTO resolveRouteScope(DataSyncTaskEntity task, DataSyncTableRouteEntity route) {
        DataSyncMappingPreviewDTO scope = new DataSyncMappingPreviewDTO();
        scope.setSourceDataSourceId(task.getSourceDataSourceId());
        scope.setSourceDatabase(route.getSourceDatabase());
        scope.setSourceSchema(route.getSourceSchema());
        scope.setSourceTable(route.getSourceTable());
        scope.setTargetDataSourceId(task.getTargetDataSourceId());
        scope.setTargetDatabase(route.getTargetDatabase());
        scope.setTargetSchema(route.getTargetSchema());
        scope.setTargetTable(route.getTargetTable());
        scope.setAutoCreateTable(Boolean.TRUE.equals(route.getAutoCreateTable()));
        scope.setMapping(mappingConfig(route.getMappingConfig()));
        return resolveMappingScope(scope);
    }

    private boolean executableDefinitionChanged(
            DataSyncTaskEntity entity, DataSyncTaskDTO dto, DataSyncMappingPreviewDTO resolvedScope) {
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
                || autoCreateTable(entity) != Boolean.TRUE.equals(dto.getAutoCreateTable())
                || taskWriteMode(entity) != requireWriteMode(dto.getWriteMode())
                || !jsonEquals(normalizedMappingConfigJson(entity.getMappingConfig()), mappingConfigJson(dto))
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
            DataSyncTaskEntity entity, DataSyncTaskDTO dto, DataSyncMappingPreviewDTO resolvedScope) {
        entity.setSourceDataSourceId(dto.getSourceDataSourceId().trim());
        entity.setSourceDatabase(resolvedScope.getSourceDatabase());
        entity.setSourceSchema(resolvedScope.getSourceSchema());
        entity.setSourceTable(dto.getSourceTable().trim());
        entity.setTargetDataSourceId(dto.getTargetDataSourceId().trim());
        entity.setTargetDatabase(resolvedScope.getTargetDatabase());
        entity.setTargetSchema(resolvedScope.getTargetSchema());
        entity.setTargetTable(dto.getTargetTable().trim());
        entity.setAutoCreateTable(Boolean.TRUE.equals(dto.getAutoCreateTable()));
        entity.setMappingConfig(mappingConfigJson(dto));
        entity.setWriteMode(requireWriteMode(dto.getWriteMode()));
        entity.setRuntimeConfig(runtimeConfigJson(entity.getSyncType(), dto));
        entity.setRetryPolicy(retryPolicyJson(dto));
        entity.setRemark(StringUtils.trimToNull(dto.getRemark()));
    }

    private void validateTaskDefinition(
            DataSyncType syncType, DataSyncTaskDTO dto, DataSyncMappingPreviewDTO resolvedScope) {
        validateWriteMode(syncType, dto.getWriteMode());
        DataSyncMappingDTO mapping = normalizeMapping(dto.getMapping());
        resolvedScope.setMapping(mapping);
        if (syncType == DataSyncType.OFFLINE) {
            validateOfflineUpsertTarget(
                    dto.getSourceDataSourceId(), dto.getTargetDataSourceId(), resolvedScope, dto.getWriteMode());
            return;
        }

        validateRealtimeTopology(dto.getSourceDataSourceId(), dto.getTargetDataSourceId(), resolvedScope);
    }

    private void validateOfflineUpsertTarget(
            String sourceDataSourceId,
            String targetDataSourceId,
            DataSyncMappingPreviewDTO resolvedScope,
            DataSyncWriteMode writeMode) {
        if (writeMode != DataSyncWriteMode.UPSERT) return;

        LogicalTable sourceLogicalTable = sourceTableIntrospector.introspect(
                sourceDataSourceId,
                resolvedScope.getSourceDatabase(),
                resolvedScope.getSourceSchema(),
                resolvedScope.getSourceTable());
        ResolvedSchemaMapping resolvedMapping = resolveSchemaMapping(sourceLogicalTable, resolvedScope.getMapping());
        DataSourceTablePathDTO targetPath = tablePath(
                resolvedScope.getTargetDatabase(), resolvedScope.getTargetSchema(), resolvedScope.getTargetTable());

        boolean targetExists = dataSourceService
                .findCatalogTable(targetDataSourceId, targetPath)
                .isPresent();
        if (!targetExists) {
            if (!Boolean.TRUE.equals(resolvedScope.getAutoCreateTable())) {
                throw new DataSyncException(DataSyncErrorCode.TARGET_TABLE_NOT_FOUND);
            }
            if (sourceLogicalTable.primaryKeys().isEmpty()) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "UPSERT 自动建表要求来源表包含主键");
            }
            if (!allPrimaryKeysMapped(sourceLogicalTable, resolvedMapping)) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "UPSERT 自动建表要求字段映射包含来源表全部主键");
            }
            return;
        }

        List<DataSourceCatalogColumnVO> targetColumns =
                dataSourceService.queryCatalogColumns(targetDataSourceId, targetPath);
        Set<String> targetPrimaryKeys = DataSyncCatalogColumns.primaryKeyNames(targetColumns);
        if (targetPrimaryKeys.isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "UPSERT 写入要求目标表存在主键");
        }
        Set<String> mappedTargetColumns = resolvedMapping.targetTable().columns().stream()
                .map(column -> column.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (!mappedTargetColumns.containsAll(targetPrimaryKeys)) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "UPSERT 写入要求字段映射包含目标表全部主键");
        }
    }

    private void validateRealtimeTopology(
            String sourceDataSourceId, String targetDataSourceId, DataSyncMappingPreviewDTO resolvedScope) {
        DataSourceVO source = dataSourceService.queryDataSource(sourceDataSourceId);
        DataSourceVO target = dataSourceService.queryDataSource(targetDataSourceId);
        if (!"MYSQL".equals(source.getDbType())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步来源数据源仅支持 MYSQL");
        }
        if (!REALTIME_TARGET_TYPES.contains(target.getDbType())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步目标数据源仅支持 MYSQL / POSTGRE_SQL / ORACLE");
        }

        LogicalTable sourceLogicalTable = sourceTableIntrospector.introspect(
                sourceDataSourceId,
                resolvedScope.getSourceDatabase(),
                resolvedScope.getSourceSchema(),
                resolvedScope.getSourceTable());
        if (sourceLogicalTable.primaryKeys().isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步来源表必须包含主键");
        }
        ResolvedSchemaMapping resolvedMapping = resolveSchemaMapping(sourceLogicalTable, resolvedScope.getMapping());
        if (!allPrimaryKeysMapped(sourceLogicalTable, resolvedMapping)) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步字段映射必须包含来源表全部主键");
        }

        DataSourceTablePathDTO targetPath = tablePath(
                resolvedScope.getTargetDatabase(), resolvedScope.getTargetSchema(), resolvedScope.getTargetTable());
        boolean targetExists = dataSourceService
                .findCatalogTable(targetDataSourceId, targetPath)
                .isPresent();
        if (!targetExists) {
            if (!Boolean.TRUE.equals(resolvedScope.getAutoCreateTable())) {
                throw new DataSyncException(DataSyncErrorCode.TARGET_TABLE_NOT_FOUND);
            }
            return;
        }

        List<DataSourceCatalogColumnVO> targetColumns =
                dataSourceService.queryCatalogColumns(targetDataSourceId, targetPath);
        Set<String> targetPrimaryKeys = DataSyncCatalogColumns.primaryKeyNames(targetColumns);
        Set<String> mappedPrimaryKeys =
                normalizedKeys(resolvedMapping.targetTable().primaryKeys());
        if (!mappedPrimaryKeys.equals(targetPrimaryKeys)) {
            String message = resolvedScope.getMapping() == null ? "实时同步目标表主键必须与来源表主键一致" : "实时同步目标表主键必须与映射后的来源主键一致";
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, message);
        }
    }

    private String mappingConfigJson(DataSyncTaskDTO dto) {
        DataSyncMappingDTO mapping = normalizeMapping(dto.getMapping());
        return mapping == null ? null : JSONUtils.toJson(mapping);
    }

    private String normalizedMappingConfigJson(String json) {
        DataSyncMappingDTO mapping = mappingConfig(json);
        return mapping == null ? null : JSONUtils.toJson(mapping);
    }

    private DataSyncMappingDTO mappingConfig(String json) {
        if (StringUtils.isBlank(json)) return null;
        return normalizeMapping(JSONUtils.parseObject(json, DataSyncMappingDTO.class));
    }

    private DataSyncMappingDTO normalizeMapping(DataSyncMappingDTO mapping) {
        if (mapping == null) return null;
        if (CollectionUtils.isEmpty(mapping.getColumns())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "字段映射不能为空");
        }
        if (mapping.getColumns().size() > 1024) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "字段映射不能超过 1024 项");
        }

        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        List<DataSyncColumnMappingDTO> columns =
                new ArrayList<>(mapping.getColumns().size());
        for (DataSyncColumnMappingDTO item : mapping.getColumns()) {
            String source = StringUtils.trimToNull(item == null ? null : item.getSource());
            String target = StringUtils.trimToNull(item == null ? null : item.getTarget());
            if (source == null || target == null) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "字段映射来源和目标不能为空");
            }
            if (source.length() > 128 || target.length() > 128) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "字段映射名称不能超过 128 个字符");
            }
            if (!sources.add(source.toLowerCase(Locale.ROOT))) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "来源字段不能重复映射：" + source);
            }
            if (!targets.add(target.toLowerCase(Locale.ROOT))) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "目标字段不能被重复映射：" + target);
            }

            DataSyncColumnMappingDTO normalized = new DataSyncColumnMappingDTO();
            normalized.setSource(source);
            normalized.setTarget(target);
            columns.add(normalized);
        }

        DataSyncMappingDTO normalized = new DataSyncMappingDTO();
        normalized.setColumns(List.copyOf(columns));
        return normalized;
    }

    private ResolvedSchemaMapping resolveSchemaMapping(LogicalTable sourceLogicalTable, DataSyncMappingDTO mapping) {
        try {
            return schemaMappingResolver.resolve(sourceLogicalTable, schemaColumnMappings(mapping));
        } catch (IllegalArgumentException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, exception.getMessage(), exception);
        }
    }

    private List<SchemaColumnMapping> schemaColumnMappings(DataSyncMappingDTO mapping) {
        if (mapping == null) return null;
        return mapping.getColumns().stream()
                .map(column -> new SchemaColumnMapping(column.getSource(), column.getTarget()))
                .toList();
    }

    private boolean allPrimaryKeysMapped(LogicalTable sourceLogicalTable, ResolvedSchemaMapping resolvedMapping) {
        return normalizedKeys(sourceLogicalTable.primaryKeys())
                .equals(normalizedKeys(resolvedMapping.sourceTable().primaryKeys()));
    }

    private Set<String> normalizedKeys(List<String> keys) {
        Set<String> result = new HashSet<>();
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                result.add(key.toLowerCase(Locale.ROOT));
            }
        }
        return result;
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

    private DataSyncMappingPreviewVO requireCompatibleMapping(DataSyncMappingPreviewDTO dto) {
        DataSyncMappingPreviewVO preview = previewResolvedMapping(dto);
        if (preview.isCompatible()) return preview;

        if (!preview.isTargetTableExists()) {
            if (Boolean.TRUE.equals(dto.getAutoCreateTable())
                    && !preview.getUnsupportedReasons().isEmpty()) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE,
                        String.join("；", preview.getUnsupportedReasons()));
            }
            throw new DataSyncException(DataSyncErrorCode.TARGET_TABLE_NOT_FOUND);
        }
        throw new DataSyncException(DataSyncErrorCode.FIELD_MAPPING_INCOMPATIBLE);
    }

    private DataSyncDefinitionSnapshotVO definitionSnapshot(
            DataSyncTaskEntity task, List<DataSyncTableRouteEntity> routes) {
        if (routes == null || routes.isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "同步任务缺少可冻结的 Table Route");
        }

        DataSourceVO source = dataSourceService.queryDataSource(task.getSourceDataSourceId());
        DataSourceVO target = dataSourceService.queryDataSource(task.getTargetDataSourceId());
        DataSyncRuntimeConfigVO taskRuntimeConfig =
                task.getSyncType() == DataSyncType.OFFLINE ? toRuntimeConfigVO(task.getRuntimeConfig()) : null;
        DataSourceConnection sourceConnection = task.getSyncType() == DataSyncType.OFFLINE
                        && taskRuntimeConfig.getPolicy() == DataSyncRuntimePolicy.AUTO
                ? dataSourceService.resolveRuntimeConnection(task.getSourceDataSourceId())
                : null;

        List<DataSyncTableRouteSnapshotVO> routeSnapshots = routes.stream()
                .map(route -> tableRouteSnapshot(task, route, source, target, taskRuntimeConfig, sourceConnection))
                .toList();
        DataSyncTableRouteSnapshotVO compatibilityRoute = routeSnapshots.get(0);

        DataSyncDefinitionSnapshotVO snapshot = new DataSyncDefinitionSnapshotVO();
        snapshot.setTaskId(task.getId());
        snapshot.setTaskName(task.getName());
        snapshot.setTaskVersion(task.getDefinitionVersion());
        snapshot.setSyncType(task.getSyncType().name());
        snapshot.setWriteMode(taskWriteMode(task).name());
        snapshot.setRetryPolicy(toRetryPolicyVO(task.getRetryPolicy()));
        snapshot.setTableRoutes(routeSnapshots);

        // PR2 keeps the existing single-route Runtime contract alive by projecting the first
        // frozen Route onto the historical root fields. PR3 will execute tableRoutes directly.
        snapshot.setSource(compatibilityRoute.getSource());
        snapshot.setTarget(compatibilityRoute.getTarget());
        snapshot.setAutoCreateTable(compatibilityRoute.getAutoCreateTable());
        snapshot.setMapping(compatibilityRoute.getMapping());
        if (task.getSyncType() == DataSyncType.REALTIME) {
            snapshot.setRealtimeConfig(toRealtimeConfigVO(task.getRuntimeConfig()));
        } else {
            snapshot.setRuntimeConfig(compatibilityRoute.getRuntimeConfig());
            snapshot.setOfflineRuntimePlan(compatibilityRoute.getOfflineRuntimePlan());
        }
        return snapshot;
    }

    private DataSyncTableRouteSnapshotVO tableRouteSnapshot(
            DataSyncTaskEntity task,
            DataSyncTableRouteEntity route,
            DataSourceVO source,
            DataSourceVO target,
            DataSyncRuntimeConfigVO taskRuntimeConfig,
            DataSourceConnection sourceConnection) {
        DataSyncEndpointSnapshotVO sourceEndpoint =
                endpointSnapshot(source, route.getSourceDatabase(), route.getSourceSchema(), route.getSourceTable());
        DataSyncEndpointSnapshotVO targetEndpoint =
                endpointSnapshot(target, route.getTargetDatabase(), route.getTargetSchema(), route.getTargetTable());

        DataSyncTableRouteSnapshotVO snapshot = new DataSyncTableRouteSnapshotVO();
        snapshot.setRouteId(route.getId());
        snapshot.setSortOrder(route.getSortOrder());
        snapshot.setSource(sourceEndpoint);
        snapshot.setTarget(targetEndpoint);
        snapshot.setAutoCreateTable(Boolean.TRUE.equals(route.getAutoCreateTable()));
        snapshot.setMapping(toMappingVO(route.getMappingConfig()));

        if (task.getSyncType() != DataSyncType.OFFLINE) {
            return snapshot;
        }
        if (taskRuntimeConfig.getPolicy() != DataSyncRuntimePolicy.AUTO) {
            snapshot.setRuntimeConfig(taskRuntimeConfig);
            return snapshot;
        }

        LogicalTable sourceLogicalTable = sourceTableIntrospector.introspect(
                task.getSourceDataSourceId(),
                sourceEndpoint.getDatabase(),
                sourceEndpoint.getSchema(),
                sourceEndpoint.getTable());
        ResolvedSchemaMapping resolvedMapping =
                resolveSchemaMapping(sourceLogicalTable, mappingConfig(route.getMappingConfig()));
        OfflineRuntimePlan runtimePlan = offlineRuntimePlanner.plan(
                sourceConnection,
                new DataSourceTablePath(
                        sourceEndpoint.getDatabase(), sourceEndpoint.getSchema(), sourceEndpoint.getTable()),
                resolvedMapping.sourceTable(),
                target.getDbType(),
                taskRuntimeConfig);
        snapshot.setRuntimeConfig(runtimePlan.effectiveConfig());
        snapshot.setOfflineRuntimePlan(runtimePlan.summary());
        return snapshot;
    }

    private void createTableExecutions(
            String workspaceId, String executionId, List<DataSyncTableRouteSnapshotVO> routeSnapshots) {
        if (routeSnapshots == null || routeSnapshots.isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.EXECUTION_FAILED, "Execution 缺少冻结 Table Route");
        }
        for (DataSyncTableRouteSnapshotVO route : routeSnapshots) {
            DataSyncTableExecutionEntity tableExecution = new DataSyncTableExecutionEntity();
            tableExecution.setWorkspaceId(workspaceId);
            tableExecution.setExecutionId(executionId);
            tableExecution.setRouteId(route.getRouteId());
            tableExecution.setRouteOrder(route.getSortOrder());
            tableExecution.setStatus(DataSyncTableExecutionStatus.PLANNED);
            tableExecution.setCurrentAttempt(0);
            tableExecution.setReadRows(0L);
            tableExecution.setWriteRows(0L);
            tableExecution.initCreate();
            if (tableExecutionRepository.add(tableExecution) == null) {
                throw new DataSyncException(DataSyncErrorCode.EXECUTION_FAILED, "创建 Table Execution 失败");
            }
        }
    }

    private DataSyncEndpointSnapshotVO endpointSnapshot(
            DataSourceVO dataSource, String database, String schema, String table) {
        DataSyncEndpointSnapshotVO endpoint = new DataSyncEndpointSnapshotVO();
        endpoint.setDataSourceId(dataSource.getId());
        endpoint.setDataSourceName(dataSource.getName());
        endpoint.setDataSourceType(dataSource.getDbType());
        endpoint.setDatabase(database);
        endpoint.setSchema(schema);
        endpoint.setTable(table);
        return endpoint;
    }

    private void submitAfterCommit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        throw new DataSyncException(DataSyncErrorCode.ENGINE_UNAVAILABLE);
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

    private TargetColumnPlan findPlanColumn(TargetTablePlan plan, String name) {
        if (name == null) return null;
        return plan.columns().stream()
                .filter(column -> column.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private DataSyncFieldMappingVO toAutoCreateFieldMapping(
            SchemaColumnMapping resolved, DataSourceCatalogColumnVO source, TargetColumnPlan target) {
        DataSyncFieldMappingVO mapping = new DataSyncFieldMappingVO();
        mapping.setSourceName(source == null ? resolved.source() : source.getName());
        mapping.setSourceType(source == null ? null : source.getTypeName());
        mapping.setTargetName(target == null ? resolved.target() : target.name());
        mapping.setTargetType(target == null ? null : target.nativeType());
        mapping.setCompatible(source != null && target != null && target.supported());
        if (source == null) {
            mapping.setMessage("来源表缺少映射字段");
        } else if (target == null) {
            mapping.setMessage("目标建表规划缺少映射字段");
        } else if (!target.supported()) {
            mapping.setMessage(target.unsupportedReason());
        }
        return mapping;
    }

    private DataSyncFieldMappingVO toFieldMapping(
            SchemaColumnMapping resolved, DataSourceCatalogColumnVO source, DataSourceCatalogColumnVO target) {
        DataSyncFieldMappingVO mapping = new DataSyncFieldMappingVO();
        mapping.setSourceName(source == null ? resolved.source() : source.getName());
        mapping.setSourceType(source == null ? null : source.getTypeName());
        mapping.setTargetName(target == null ? resolved.target() : target.getName());
        mapping.setTargetType(target == null ? null : target.getTypeName());
        mapping.setCompatible(source != null && target != null && compatibleType(source, target));
        if (source == null) {
            mapping.setMessage("来源表缺少映射字段");
        } else if (target == null) {
            mapping.setMessage("目标表缺少映射字段：" + resolved.target());
        } else if (!mapping.isCompatible()) {
            mapping.setMessage("字段类型或容量不兼容");
        }
        return mapping;
    }

    private boolean compatibleType(DataSourceCatalogColumnVO source, DataSourceCatalogColumnVO target) {
        DataSourceColumn sourceColumn = DataSyncCatalogColumns.toColumn(source);
        DataSourceColumn targetColumn = DataSyncCatalogColumns.toColumn(target);
        if (sourceColumn == null || targetColumn == null) return false;
        try {
            YakColumn sourceYakColumn = JdbcSchemaMapper.toYakColumn(sourceColumn);
            YakColumn targetYakColumn = JdbcSchemaMapper.toYakColumn(targetColumn);
            return JdbcSchemaCompatibility.isCompatible(sourceYakColumn, targetYakColumn);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private DataSyncMappingPreviewDTO resolveMappingScope(DataSyncMappingPreviewDTO dto) {
        DataSourceVO source = dataSourceService.queryDataSource(dto.getSourceDataSourceId());
        DataSourceVO target = dataSourceService.queryDataSource(dto.getTargetDataSourceId());

        DataSyncMappingPreviewDTO resolved = BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class);
        resolved.setSourceDatabase(scopeValue(source.getDatabase(), dto.getSourceDatabase()));
        resolved.setSourceSchema(scopeValue(source.getSchema(), dto.getSourceSchema()));
        resolved.setTargetDatabase(scopeValue(target.getDatabase(), dto.getTargetDatabase()));
        resolved.setTargetSchema(scopeValue(target.getSchema(), dto.getTargetSchema()));
        return resolved;
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

    private boolean autoCreateTable(DataSyncTaskEntity task) {
        return Boolean.TRUE.equals(task.getAutoCreateTable());
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
        Set<String> sourcePaths = new HashSet<>();
        Set<String> targetPaths = new HashSet<>();
        for (DataSyncTableRouteDTO raw : requested) {
            if (raw == null) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "表级 Route 不能为空");
            }
            DataSyncTableRouteDTO route = BeanCopyUtils.copy(raw, DataSyncTableRouteDTO.class);
            route.setId(StringUtils.trimToNull(route.getId()));
            String sourceTable = StringUtils.trimToNull(route.getSourceTable());
            String targetTable = StringUtils.trimToNull(route.getTargetTable());
            if (sourceTable == null
                    || targetTable == null
                    || sourceTable.length() > 128
                    || targetTable.length() > 128) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "来源表或目标表名称不合法");
            }
            route.setSourceTable(sourceTable);
            route.setTargetTable(targetTable);
            route.setMapping(normalizeMapping(route.getMapping()));

            DataSyncMappingPreviewDTO request = new DataSyncMappingPreviewDTO();
            request.setSourceDataSourceId(task.getSourceDataSourceId());
            request.setSourceDatabase(route.getSourceDatabase());
            request.setSourceSchema(route.getSourceSchema());
            request.setSourceTable(sourceTable);
            request.setTargetDataSourceId(task.getTargetDataSourceId());
            request.setTargetDatabase(route.getTargetDatabase());
            request.setTargetSchema(route.getTargetSchema());
            request.setTargetTable(targetTable);
            request.setAutoCreateTable(Boolean.TRUE.equals(route.getAutoCreateTable()));
            request.setMapping(route.getMapping());

            DataSyncMappingPreviewDTO scope = resolveMappingScope(request);
            String sourceKey = tableIdentity(scope.getSourceDatabase(), scope.getSourceSchema(), sourceTable);
            String targetKey = tableIdentity(scope.getTargetDatabase(), scope.getTargetSchema(), targetTable);
            if (!sourcePaths.add(sourceKey)) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "来源表不能重复：" + sourceTable);
            }
            if (!targetPaths.add(targetKey)) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "目标表不能重复：" + targetTable);
            }
            validateOfflineUpsertTarget(
                    task.getSourceDataSourceId(), task.getTargetDataSourceId(), scope, task.getWriteMode());
            requireCompatibleMapping(scope);

            route.setSourceDatabase(scope.getSourceDatabase());
            route.setSourceSchema(scope.getSourceSchema());
            route.setTargetDatabase(scope.getTargetDatabase());
            route.setTargetSchema(scope.getTargetSchema());
            normalized.add(route);
        }

        // v1.2 Runtime / 列表继续使用第一条 Route 的兼容投影，不以当前 UI 选中的表覆盖它。
        DataSyncTableRouteDTO first = normalized.get(0);
        task.setSourceDatabase(first.getSourceDatabase());
        task.setSourceSchema(first.getSourceSchema());
        task.setSourceTable(first.getSourceTable());
        task.setTargetDatabase(first.getTargetDatabase());
        task.setTargetSchema(first.getTargetSchema());
        task.setTargetTable(first.getTargetTable());
        task.setAutoCreateTable(first.getAutoCreateTable());
        task.setMapping(first.getMapping());
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
        route.setAutoCreateTable(autoCreateTable(task));
        route.setMappingConfig(task.getMappingConfig());
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
                && Boolean.TRUE.equals(route.getAutoCreateTable()) == autoCreateTable(task)
                && jsonEquals(
                        normalizedMappingConfigJson(route.getMappingConfig()),
                        normalizedMappingConfigJson(task.getMappingConfig()))
                && Objects.equals(route.getSortOrder(), 0);
    }

    private DataSyncTaskVO toTaskListVO(DataSyncTaskEntity source, DataSyncScheduleEntity schedule) {
        DataSyncTaskVO target = toTaskVO(source, false);
        target.setMapping(null);
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
                "mappingConfig",
                "runtimeConfig",
                "retryPolicy");
        target.setSyncType(
                source.getSyncType() == null ? null : source.getSyncType().name());
        target.setStatus(taskStatus(source).name());
        target.setDesiredState(taskDesiredState(source).name());
        target.setWriteMode(taskWriteMode(source).name());
        target.setAutoCreateTable(autoCreateTable(source));
        target.setMapping(toMappingVO(source.getMappingConfig()));
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
        DataSyncTableRouteVO target = BeanCopyUtils.copy(source, DataSyncTableRouteVO.class, "mappingConfig");
        target.setAutoCreateTable(Boolean.TRUE.equals(source.getAutoCreateTable()));
        target.setMapping(toMappingVO(source.getMappingConfig()));
        return target;
    }

    private DataSyncRuntimeConfigVO toRuntimeConfigVO(String json) {
        return BeanCopyUtils.copy(runtimeConfig(json), DataSyncRuntimeConfigVO.class);
    }

    private DataSyncRealtimeConfigVO toRealtimeConfigVO(String json) {
        return BeanCopyUtils.copy(realtimeConfig(json), DataSyncRealtimeConfigVO.class);
    }

    private DataSyncMappingVO toMappingVO(String json) {
        DataSyncMappingDTO source = mappingConfig(json);
        if (source == null) return null;

        DataSyncMappingVO target = new DataSyncMappingVO();
        target.setColumns(source.getColumns().stream()
                .map(item -> BeanCopyUtils.copy(item, DataSyncColumnMappingVO.class))
                .toList());
        return target;
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
}
