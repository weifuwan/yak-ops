package io.yak.ops.dao.repository.task;

import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.dao.entity.task.InstanceEntity;
import java.util.Optional;

/** Workspace-scoped Task Instance persistence and idempotent request lookup. */
public interface InstanceRepository {

    /**
     * Inserts a run; the database enforces uniqueness of its request identity.
     *
     * @param instance admitted run with a pinned Definition version
     */
    void add(InstanceEntity instance);

    /**
     * Finds a business run inside its owning Workspace.
     *
     * @param workspaceId authorized Workspace identifier
     * @param instanceId stable run identifier
     * @return the matching run, or empty when it does not exist in this Workspace
     */
    Optional<InstanceEntity> findById(String workspaceId, String instanceId);

    /**
     * Finds the original run for an idempotent submission key.
     *
     * @param workspaceId authorized Workspace identifier
     * @param trigger submission namespace
     * @param requestId caller-stable request identity
     * @return the original run, including terminal runs, or empty when absent
     */
    Optional<InstanceEntity> findByRequest(String workspaceId, TaskTriggerType trigger, String requestId);
}
