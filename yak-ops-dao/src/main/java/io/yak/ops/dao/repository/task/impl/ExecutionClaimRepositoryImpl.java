package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import io.yak.ops.dao.mapper.task.ExecutionClaimMapper;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Workspace-owned target claims, acquired and released in business transactions. */
@Repository
public class ExecutionClaimRepositoryImpl implements ExecutionClaimRepository {

    @Resource
    private ExecutionClaimMapper mapper;

    @Override
    public void add(ExecutionClaimEntity claim) {
        mapper.insert(claim);
    }

    @Override
    public List<ExecutionClaimEntity> findByInstance(String workspaceId, String instanceId) {
        return mapper.selectList(Wrappers.<ExecutionClaimEntity>lambdaQuery()
                .eq(ExecutionClaimEntity::getWorkspaceId, workspaceId)
                .eq(ExecutionClaimEntity::getInstanceId, instanceId)
                .orderByAsc(ExecutionClaimEntity::getResourceType, ExecutionClaimEntity::getResourceKey));
    }

    @Override
    public int releaseOwned(String workspaceId, String instanceId) {
        return mapper.delete(Wrappers.<ExecutionClaimEntity>lambdaQuery()
                .eq(ExecutionClaimEntity::getWorkspaceId, workspaceId)
                .eq(ExecutionClaimEntity::getInstanceId, instanceId));
    }
}
