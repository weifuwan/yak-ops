package io.yak.ops.dao.repository.task;

import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.dao.entity.task.InstanceEntity;
import java.util.Optional;

/** Workspace-scoped Task Instance persistence and idempotent request lookup. */
public interface InstanceRepository {

    void add(InstanceEntity instance);

    Optional<InstanceEntity> findById(String workspaceId, String instanceId);

    Optional<InstanceEntity> findByRequest(String workspaceId, TaskTriggerType trigger, String requestId);
}
