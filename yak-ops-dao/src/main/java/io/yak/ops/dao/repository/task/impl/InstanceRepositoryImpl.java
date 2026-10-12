package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.mapper.task.InstanceMapper;
import io.yak.ops.dao.repository.task.InstanceRepository;
import jakarta.annotation.Resource;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Task Instance persistence with unique Workspace-scoped request identity. */
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
    public Optional<InstanceEntity> findByRequest(String workspaceId, TaskTriggerType trigger, String requestId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .eq(InstanceEntity::getTriggerType, trigger)
                .eq(InstanceEntity::getRequestId, requestId)));
    }
}
