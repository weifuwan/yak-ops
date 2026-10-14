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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Workspace-scoped Instance queries and guarded business lifecycle transitions. */
@Repository
public class InstanceRepositoryImpl implements InstanceRepository {

    @Resource
    private InstanceMapper mapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

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
    public Optional<InstanceEntity> findByIdForLogCleanup(String instanceId) {
        return Optional.ofNullable(mapper.selectById(instanceId));
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
    public Optional<InstanceEntity> findByRequestForUpdate(
            String workspaceId, TaskTriggerType trigger, String requestId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .eq(InstanceEntity::getTriggerType, trigger)
                .eq(InstanceEntity::getRequestId, requestId)
                .last("FOR UPDATE")));
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
    public List<InstanceEntity> latest(String workspaceId, List<String> definitionIds, boolean activeOnly) {
        if (definitionIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(definitionIds.size(), "?"));
        String filter =
                activeOnly ? " AND status IN ('SUBMITTED', 'LAUNCHING', 'RUNNING', 'CANCELLING', 'RECONCILING')" : "";
        String sql = """
                SELECT id FROM (
                    SELECT id, ROW_NUMBER() OVER (
                        PARTITION BY definition_id ORDER BY create_time DESC, id DESC
                    ) AS row_rank
                    FROM yak_ops_task_instance
                    WHERE workspace_id = ? AND definition_id IN (%s)%s
                ) ranked WHERE row_rank = 1
                """.formatted(placeholders, filter);
        List<Object> parameters = new ArrayList<>(definitionIds.size() + 1);
        parameters.add(workspaceId);
        parameters.addAll(definitionIds);
        List<String> ids = jdbcTemplate.queryForList(sql, String.class, parameters.toArray());
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .in(InstanceEntity::getId, ids));
    }

    @Override
    public boolean updateProgress(String workspaceId, String instanceId, String dispatchToken, int progressPercent) {
        if (progressPercent < 0 || progressPercent > 99) {
            throw new IllegalArgumentException("Measured progress must be between 0 and 99");
        }
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .eq(InstanceEntity::getDispatchToken, dispatchToken)
                                .in(
                                        InstanceEntity::getStatus,
                                        TaskInstanceStatus.LAUNCHING,
                                        TaskInstanceStatus.RUNNING,
                                        TaskInstanceStatus.CANCELLING)
                                .set(InstanceEntity::getProgressPercent, progressPercent))
                == 1;
    }

    @Override
    public List<InstanceEntity> pending() {
        return mapper.selectList(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getStatus, TaskInstanceStatus.SUBMITTED)
                .orderByAsc(InstanceEntity::getCreateTime));
    }

    @Override
    public List<InstanceEntity> activeByOtherOwner(String ownerIncarnation) {
        return mapper.selectList(Wrappers.<InstanceEntity>lambdaQuery()
                .in(
                        InstanceEntity::getStatus,
                        TaskInstanceStatus.LAUNCHING,
                        TaskInstanceStatus.RUNNING,
                        TaskInstanceStatus.CANCELLING)
                .ne(InstanceEntity::getOwnerIncarnation, ownerIncarnation));
    }

    @Override
    public boolean claim(String workspaceId, String instanceId, String ownerIncarnation, String dispatchToken) {
        if (ownerIncarnation == null
                || ownerIncarnation.isBlank()
                || dispatchToken == null
                || dispatchToken.isBlank()) {
            throw new IllegalArgumentException("Dispatcher identity is required");
        }
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .eq(InstanceEntity::getStatus, TaskInstanceStatus.SUBMITTED)
                                .set(InstanceEntity::getStatus, TaskInstanceStatus.LAUNCHING)
                                .set(InstanceEntity::getOwnerIncarnation, ownerIncarnation)
                                .set(InstanceEntity::getDispatchToken, dispatchToken)
                                .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                                .setSql("state_version = state_version + 1"))
                == 1;
    }

    @Override
    public boolean markRunning(
            String workspaceId, String instanceId, String dispatchToken, String engine, String runtimeId) {
        int updated = mapper.update(
                null,
                Wrappers.<InstanceEntity>lambdaUpdate()
                        .eq(InstanceEntity::getWorkspaceId, workspaceId)
                        .eq(InstanceEntity::getId, instanceId)
                        .eq(InstanceEntity::getDispatchToken, dispatchToken)
                        .eq(InstanceEntity::getStatus, TaskInstanceStatus.LAUNCHING)
                        .set(InstanceEntity::getStatus, TaskInstanceStatus.RUNNING)
                        .set(InstanceEntity::getRuntimeEngine, engine)
                        .set(InstanceEntity::getRuntimeId, runtimeId)
                        .set(InstanceEntity::getStartTime, LocalDateTime.now())
                        .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                        .setSql("state_version = state_version + 1"));
        if (updated == 1) {
            return true;
        }
        // Cancellation may win before the physical JobClient becomes observable.
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .eq(InstanceEntity::getDispatchToken, dispatchToken)
                                .eq(InstanceEntity::getStatus, TaskInstanceStatus.CANCELLING)
                                .isNull(InstanceEntity::getRuntimeId)
                                .set(InstanceEntity::getRuntimeEngine, engine)
                                .set(InstanceEntity::getRuntimeId, runtimeId)
                                .set(InstanceEntity::getStartTime, LocalDateTime.now())
                                .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                                .setSql("state_version = state_version + 1"))
                == 1;
    }

    @Override
    public boolean cancelSubmitted(String workspaceId, String instanceId) {
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .eq(InstanceEntity::getStatus, TaskInstanceStatus.SUBMITTED)
                                .set(InstanceEntity::getStatus, TaskInstanceStatus.CANCELED)
                                .set(InstanceEntity::getEndTime, LocalDateTime.now())
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
                                .in(InstanceEntity::getStatus, TaskInstanceStatus.LAUNCHING, TaskInstanceStatus.RUNNING)
                                .set(InstanceEntity::getStatus, TaskInstanceStatus.CANCELLING)
                                .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                                .setSql("state_version = state_version + 1"))
                == 1;
    }

    @Override
    public boolean markTerminal(
            String workspaceId, String instanceId, String dispatchToken, TaskInstanceStatus status, String reason) {
        if (!status.isTerminal()) {
            throw new IllegalArgumentException("Terminal Task Instance status is required");
        }
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .eq(InstanceEntity::getDispatchToken, dispatchToken)
                                .in(
                                        InstanceEntity::getStatus,
                                        TaskInstanceStatus.LAUNCHING,
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
    public boolean markReconciling(String workspaceId, String instanceId, String dispatchToken) {
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .eq(InstanceEntity::getDispatchToken, dispatchToken)
                                .in(
                                        InstanceEntity::getStatus,
                                        TaskInstanceStatus.LAUNCHING,
                                        TaskInstanceStatus.RUNNING,
                                        TaskInstanceStatus.CANCELLING)
                                .set(InstanceEntity::getStatus, TaskInstanceStatus.RECONCILING)
                                .set(InstanceEntity::getFailureCode, "EXECUTION_OWNER_UNCERTAIN")
                                .set(InstanceEntity::getUpdateTime, LocalDateTime.now())
                                .setSql("state_version = state_version + 1"))
                == 1;
    }

    @Override
    public boolean updateExecutionReport(String workspaceId, String instanceId, String dispatchToken, String report) {
        return mapper.update(
                        null,
                        Wrappers.<InstanceEntity>lambdaUpdate()
                                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                                .eq(InstanceEntity::getId, instanceId)
                                .eq(InstanceEntity::getDispatchToken, dispatchToken)
                                .in(
                                        InstanceEntity::getStatus,
                                        TaskInstanceStatus.LAUNCHING,
                                        TaskInstanceStatus.RUNNING,
                                        TaskInstanceStatus.CANCELLING)
                                .set(InstanceEntity::getExecutionReport, report))
                == 1;
    }
}
