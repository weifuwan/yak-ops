package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasync.DataSyncScheduleService;
import io.yak.ops.business.datasync.DataSyncTaskService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncRealtimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRetryPolicyDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRuntimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTableRouteDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRealtimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskPageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataSyncTaskService 的业务职责实现。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class DataSyncTaskServiceImpl implements DataSyncTaskService {

    @Resource
    private DataSyncTaskRepository taskRepository;

    @Resource
    private DataSyncScheduleRepository scheduleRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncScheduleService scheduleService;

    @Resource
    private DataSyncTaskDefinitionValidator definitionValidator;

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
        definitionValidator.rejectMultiRouteRequest(dto);
        DataSyncTableRouteDTO resolvedScope = definitionValidator.resolveTaskScope(dto);
        definitionValidator.validateTaskDefinition(syncType, dto, resolvedScope);

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
        definitionValidator.requireSingleTableTask(entity);
        requireTaskStatus(entity, DataSyncTaskStatus.UNPUBLISHED, "已上线任务请先下线后再编辑");
        String name = StringUtils.trimToNull(dto.getName());
        if (name == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, id);
        DataSyncType syncType = requireSyncType(dto.getSyncType());
        if (entity.getSyncType() != syncType) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "同步类型创建后不允许修改");
        }
        materializeUpdatePolicies(entity, dto);
        definitionValidator.rejectMultiRouteRequest(dto);
        DataSyncTableRouteDTO resolvedScope = definitionValidator.resolveTaskScope(dto);
        definitionValidator.validateTaskDefinition(syncType, dto, resolvedScope);

        boolean executableDefinitionChanged = executableDefinitionChanged(entity, dto, resolvedScope);
        entity.setName(name);
        applyDefinition(entity, dto, resolvedScope);
        if (executableDefinitionChanged) {
            entity.setDefinitionVersion(Math.max(1, entity.getDefinitionVersion()) + 1);
        }
        entity.initUpdate(operatorUserId);

        if (taskRepository.update(workspaceId, entity) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED);
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
        definitionValidator.validatePersistedTaskDefinition(task);
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
        scheduleService.disableForTask(workspaceId, task.getId());
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
    public boolean deleteTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        requireTaskStatus(entity, DataSyncTaskStatus.UNPUBLISHED, "已上线任务请先下线后再删除");
        if (instanceRepository.existsActiveByTask(workspaceId, entity.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS);
        }
        scheduleService.deleteForTask(workspaceId, entity.getId());
        if (taskRepository.deleteById(workspaceId, entity.getId()) <= 0) {
            throw new DataSyncException(DataSyncErrorCode.DELETE_TASK_FAILED);
        }
        return true;
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

    private void applyDefinition(DataSyncTaskEntity entity, DataSyncTaskDTO dto, DataSyncTableRouteDTO resolvedScope) {
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

    private DataSyncWriteMode taskWriteMode(DataSyncTaskEntity task) {
        return task.getWriteMode() == null ? DataSyncWriteMode.APPEND : task.getWriteMode();
    }

    private DataSyncTaskStatus taskStatus(DataSyncTaskEntity task) {
        return task.getStatus() == null ? DataSyncTaskStatus.PUBLISHED : task.getStatus();
    }

    private DataSyncDesiredState taskDesiredState(DataSyncTaskEntity task) {
        return task.getDesiredState() == null ? DataSyncDesiredState.STOPPED : task.getDesiredState();
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

    private void ensureTaskNameAvailable(String workspaceId, String name, String excludeId) {
        if (taskRepository.existsByName(workspaceId, name, excludeId)) {
            throw new DataSyncException(DataSyncErrorCode.DUPLICATE_TASK_NAME);
        }
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

    private DataSyncTaskVO toTaskVO(DataSyncTaskEntity source) {
        return toTaskVOInternal(source);
    }

    private DataSyncTaskVO toTaskVOInternal(DataSyncTaskEntity source) {
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
        target.setRetryPolicy(toRetryPolicyVO(source.getRetryPolicy()));
        if (source.getSyncType() == DataSyncType.REALTIME) {
            target.setRealtimeConfig(toRealtimeConfigVO(source.getRuntimeConfig()));
        } else {
            target.setRuntimeConfig(toRuntimeConfigVO(source.getRuntimeConfig()));
        }
        return target;
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
}
