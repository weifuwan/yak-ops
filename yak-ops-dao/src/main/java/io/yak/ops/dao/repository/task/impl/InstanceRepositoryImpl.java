package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.mapper.task.InstanceMapper;
import io.yak.ops.dao.repository.task.InstanceRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Workspace-scoped Instance queries and guarded business lifecycle transitions. */
@Repository
public class InstanceRepositoryImpl implements InstanceRepository {

    @Resource
    private InstanceMapper mapper;

    @Override
    public void add(InstanceEntity instance) {
        mapper.insert(instance);
    }

    @Override
    public Optional<InstanceEntity> findById(String workspaceId, String instanceId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .eq(InstanceEntity::getId, instanceId)));
    }

    @Override
    public Optional<InstanceEntity> findByIdForUpdate(String workspaceId, String instanceId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .eq(InstanceEntity::getId, instanceId)
                .last("FOR UPDATE")));
    }

    @Override
    public Optional<InstanceEntity> findByRequest(String workspaceId, TaskTriggerType trigger, String requestId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .eq(InstanceEntity::getTriggerType, trigger)
                .eq(InstanceEntity::getRequestId, requestId)));
    }

    @Override
    public List<InstanceEntity> recent(String workspaceId, int limit) {
        return mapper.selectList(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .orderByDesc(InstanceEntity::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(200, limit))));
    }

    @Override
    public PageData<InstanceEntity> page(
            String workspaceId,
            long pageNo,
            long pageSize,
            String definitionId,
            TaskInstanceStatus status,
            String taskType) {
        Page<InstanceEntity> page = new Page<>(pageNo, pageSize);
        var selected = mapper.selectPage(
                page,
                Wrappers.<InstanceEntity>lambdaQuery()
                        .eq(InstanceEntity::getWorkspaceId, workspaceId)
                        .eq(definitionId != null, InstanceEntity::getDefinitionId, definitionId)
                        .eq(status != null, InstanceEntity::getStatus, status)
                        .eq(taskType != null, InstanceEntity::getTaskType, taskType)
                        .orderByDesc(InstanceEntity::getCreateTime));
        return new PageData<>(
                selected.getRecords(),
                selected.getTotal(),
                selected.getPages(),
                selected.getCurrent(),
                selected.getSize());
    }

    @Override
    public boolean markRunning(String workspaceId, String instanceId) {
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .eq(InstanceEntity::getStatus, TaskInstanceStatus.SUBMITTED)
                                .set(InstanceEntity::getStatus, TaskInstanceStatus.RUNNING)
                                .set(InstanceEntity::getStartTime, LocalDateTime.now())
                                .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                                .setSql("state_version = state_version + 1"))
                == 1;
    }

    @Override
    public boolean markCancelling(String workspaceId, String instanceId) {
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .in(InstanceEntity::getStatus, TaskInstanceStatus.SUBMITTED, TaskInstanceStatus.RUNNING)
                                .set(InstanceEntity::getStatus, TaskInstanceStatus.CANCELLING)
                                .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                                .setSql("state_version = state_version + 1"))
                == 1;
    }

    @Override
    public boolean markTerminal(String workspaceId, String instanceId, TaskInstanceStatus status, String reason) {
        if (!status.isTerminal()) {
            throw new IllegalArgumentException("Terminal Task Instance status is required");
        }
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .in(
                                        InstanceEntity::getStatus,
                                        TaskInstanceStatus.SUBMITTED,
                                        TaskInstanceStatus.RUNNING,
                                        TaskInstanceStatus.CANCELLING)
                                .set(InstanceEntity::getStatus, status)
                                .set(InstanceEntity::getFailureCode, reason)
                                .set(InstanceEntity::getEndTime, LocalDateTime.now())
                                .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                                .setSql("state_version = state_version + 1"))
                == 1;
    }

    @Override
    public boolean markReconciling(String workspaceId, String instanceId) {
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .in(
                                        InstanceEntity::getStatus,
                                        TaskInstanceStatus.SUBMITTED,
                                        TaskInstanceStatus.RUNNING,
                                        TaskInstanceStatus.CANCELLING)
                                .set(InstanceEntity::getStatus, TaskInstanceStatus.RECONCILING)
                                .set(InstanceEntity::getFailureCode, "EXECUTION_OWNER_UNCERTAIN")
                                .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                                .setSql("state_version = state_version + 1"))
                == 1;
    }
}
