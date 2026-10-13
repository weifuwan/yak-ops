package io.yak.ops.business.task.instance.impl;

import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

/** Workspace-scoped read operations for persisted Task Instances and Attempts. */
@Service
public class InstanceServiceImpl implements InstanceService {

    @Resource
    private InstanceRepository instances;

    @Resource
    private AttemptRepository attempts;

    @Override
    public InstanceEntity instance(String workspaceId, String instanceId) {
        return instances
                .findById(workspaceId, instanceId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    @Override
    public List<AttemptEntity> attempts(String workspaceId, String instanceId) {
        instance(workspaceId, instanceId);
        return attempts.findByInstance(workspaceId, instanceId);
    }

    @Override
    public List<InstanceEntity> recent(String workspaceId, int limit) {
        return instances.recent(workspaceId, limit);
    }

    @Override
    public PageData<InstanceEntity> page(String workspaceId, long pageNo, long pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        return instances.page(workspaceId, pageNo, pageSize);
    }
}
