package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.enums.task.TaskAttemptStatus;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.mapper.task.AttemptMapper;
import io.yak.ops.dao.repository.task.AttemptRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Durable Attempt records and atomic dispatch claim. */
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
}
