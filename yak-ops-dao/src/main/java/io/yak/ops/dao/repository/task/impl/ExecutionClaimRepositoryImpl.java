package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import io.yak.ops.dao.mapper.task.ExecutionClaimMapper;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/** Durable resource exclusion with Instance-checked release. */
@Repository
public class ExecutionClaimRepositoryImpl implements ExecutionClaimRepository {
    @Resource
    private ExecutionClaimMapper mapper;

    @Override
    public void add(ExecutionClaimEntity claim) {
        mapper.insert(claim);
    }

    @Override
    public int releaseOwned(String workspaceId, String instanceId) {
        return mapper.delete(Wrappers.<ExecutionClaimEntity>lambdaQuery()
                .eq(ExecutionClaimEntity::getWorkspaceId, workspaceId)
                .eq(ExecutionClaimEntity::getInstanceId, instanceId));
    }
}
