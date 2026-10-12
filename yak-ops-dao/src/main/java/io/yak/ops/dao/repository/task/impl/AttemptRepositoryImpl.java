package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.enums.task.TaskAttemptStatus;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.mapper.task.AttemptMapper;
import io.yak.ops.dao.repository.task.AttemptRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Durable single-dispatch attempt record with owner-token-guarded updates. */
@Repository
public class AttemptRepositoryImpl implements AttemptRepository {

    @Resource
    private AttemptMapper mapper;

    @Override
    public void add(AttemptEntity attempt) {
        mapper.insert(attempt);
    }

    @Override
    public List<AttemptEntity> findByInstance(String workspaceId, String instanceId) {
        return mapper.selectList(Wrappers.<AttemptEntity>lambdaQuery()
                .eq(AttemptEntity::getWorkspaceId, workspaceId)
                .eq(AttemptEntity::getInstanceId, instanceId)
                .orderByAsc(AttemptEntity::getAttemptNumber));
    }

    @Override
    public Optional<AttemptEntity> findById(String workspaceId, String attemptId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<AttemptEntity>lambdaQuery()
                .eq(AttemptEntity::getWorkspaceId, workspaceId)
                .eq(AttemptEntity::getId, attemptId)));
    }

    @Override
    public boolean claimPending(String workspaceId, String attemptId, String ownerIncarnation, String dispatchToken) {
        if (ownerIncarnation == null
                || ownerIncarnation.isBlank()
                || dispatchToken == null
                || dispatchToken.isBlank()) {
            throw new IllegalArgumentException("Dispatcher identity is required");
        }
        return mapper.update(
                        null,
                        Wrappers.<AttemptEntity>lambdaUpdate()
                                .eq(AttemptEntity::getWorkspaceId, workspaceId)
                                .eq(AttemptEntity::getId, attemptId)
                                .eq(AttemptEntity::getStatus, TaskAttemptStatus.PENDING)
                                .set(AttemptEntity::getStatus, TaskAttemptStatus.LAUNCHING)
                                .set(AttemptEntity::getOwnerIncarnation, ownerIncarnation)
                                .set(AttemptEntity::getDispatchToken, dispatchToken)
                                .set(AttemptEntity::getUpdateTime, LocalDateTime.now()))
                == 1;
    }

    @Override
    public boolean cancelPending(String workspaceId, String attemptId) {
        return mapper.update(
                        null,
                        Wrappers.<AttemptEntity>lambdaUpdate()
                                .eq(AttemptEntity::getWorkspaceId, workspaceId)
                                .eq(AttemptEntity::getId, attemptId)
                                .eq(AttemptEntity::getStatus, TaskAttemptStatus.PENDING)
                                .set(AttemptEntity::getStatus, TaskAttemptStatus.CANCELED)
                                .set(AttemptEntity::getEndTime, LocalDateTime.now()))
                == 1;
    }

    @Override
    public boolean markRunning(
            String workspaceId, String attemptId, String dispatchToken, String engine, String runtimeId) {
        return mapper.update(
                        null,
                        Wrappers.<AttemptEntity>lambdaUpdate()
                                .eq(AttemptEntity::getWorkspaceId, workspaceId)
                                .eq(AttemptEntity::getId, attemptId)
                                .eq(AttemptEntity::getDispatchToken, dispatchToken)
                                .eq(AttemptEntity::getStatus, TaskAttemptStatus.LAUNCHING)
                                .set(AttemptEntity::getStatus, TaskAttemptStatus.RUNNING)
                                .set(AttemptEntity::getRuntimeEngine, engine)
                                .set(AttemptEntity::getRuntimeId, runtimeId)
                                .set(AttemptEntity::getStartTime, LocalDateTime.now())
                                .set(AttemptEntity::getUpdateTime, LocalDateTime.now()))
                == 1;
    }

    @Override
    public boolean markTerminal(
            String workspaceId, String attemptId, String dispatchToken, TaskAttemptStatus status, String reason) {
        if (status != TaskAttemptStatus.FINISHED
                && status != TaskAttemptStatus.FAILED
                && status != TaskAttemptStatus.CANCELED) {
            throw new IllegalArgumentException("Terminal Task Attempt status is required");
        }
        return mapper.update(
                        null,
                        Wrappers.<AttemptEntity>lambdaUpdate()
                                .eq(AttemptEntity::getWorkspaceId, workspaceId)
                                .eq(AttemptEntity::getId, attemptId)
                                .eq(AttemptEntity::getDispatchToken, dispatchToken)
                                .in(AttemptEntity::getStatus, TaskAttemptStatus.LAUNCHING, TaskAttemptStatus.RUNNING)
                                .set(AttemptEntity::getStatus, status)
                                .set(AttemptEntity::getFailureCode, reason)
                                .set(AttemptEntity::getEndTime, LocalDateTime.now())
                                .set(AttemptEntity::getUpdateTime, LocalDateTime.now()))
                == 1;
    }

    @Override
    public List<AttemptEntity> pending() {
        return mapper.selectList(Wrappers.<AttemptEntity>lambdaQuery()
                .eq(AttemptEntity::getStatus, TaskAttemptStatus.PENDING)
                .orderByAsc(AttemptEntity::getCreateTime));
    }

    @Override
    public List<AttemptEntity> activeByOtherOwner(String ownerIncarnation) {
        return mapper.selectList(Wrappers.<AttemptEntity>lambdaQuery()
                .in(AttemptEntity::getStatus, TaskAttemptStatus.LAUNCHING, TaskAttemptStatus.RUNNING)
                .ne(AttemptEntity::getOwnerIncarnation, ownerIncarnation));
    }
}
