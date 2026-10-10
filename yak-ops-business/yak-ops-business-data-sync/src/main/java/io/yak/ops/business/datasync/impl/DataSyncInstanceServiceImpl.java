package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasync.DataSyncInstanceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSinkTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSourceTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableExecutionVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTracePageVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTraceSummaryVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstancePageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import io.yak.ops.dao.repository.datasync.SyncDefinitionRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataSyncInstanceService 的业务职责实现。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class DataSyncInstanceServiceImpl implements DataSyncInstanceService {

    @Resource
    private SyncDefinitionRepository taskRepository;

    @Resource
    private DataSyncTableExecutionRepository tableExecutionRepository;

    @Resource
    private DataSyncTableAttemptRepository tableAttemptRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncAttemptRepository attemptRepository;

    @Resource
    private SyncDefinitionValidator definitionValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized DataSyncInstanceVO runTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        SyncDefinitionEntity task = requireTask(workspaceId, id);
        requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "任务尚未上线");
        definitionValidator.validatePersistedTaskDefinition(task);
        if (instanceRepository.existsActiveByTask(workspaceId, task.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS);
        }
        throw runtimeUnavailable();
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
            SyncDefinitionEntity task =
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

    private void updateDesiredState(String workspaceId, SyncDefinitionEntity task, DataSyncDesiredState desiredState) {
        if (taskDesiredState(task) == desiredState) return;
        task.setDesiredState(desiredState);
        task.initUpdate();
        if (taskRepository.update(workspaceId, task) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "更新实时同步期望状态失败");
        }
    }

    private DataSyncDesiredState taskDesiredState(SyncDefinitionEntity task) {
        return task.getDesiredState() == null ? DataSyncDesiredState.STOPPED : task.getDesiredState();
    }

    private DataSyncTaskStatus taskStatus(SyncDefinitionEntity task) {
        return task.getStatus() == null ? DataSyncTaskStatus.PUBLISHED : task.getStatus();
    }

    private void requireTaskStatus(SyncDefinitionEntity task, DataSyncTaskStatus expected, String detail) {
        if (taskStatus(task) != expected) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK_STATUS, detail);
        }
    }

    private SyncDefinitionEntity requireTask(String workspaceId, String id) {
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

    private DataSyncAttemptVO toAttemptVO(DataSyncAttemptEntity source) {
        DataSyncAttemptVO target = BeanCopyUtils.copy(source, DataSyncAttemptVO.class, "status");
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
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

    private DataSyncException runtimeUnavailable() {
        return new DataSyncException(DataSyncErrorCode.EXECUTION_FAILED, "同步引擎尚未接入，当前无法运行同步任务");
    }
}
