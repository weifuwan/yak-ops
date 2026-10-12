package io.yak.ops.dao.repository.task;

import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.InstanceEntity;
import java.util.List;
import java.util.Optional;

/** Workspace-scoped Task Instance persistence and authoritative business transitions. */
public interface InstanceRepository {

    /** @param instance authorized and pinned run to insert */
    void add(InstanceEntity instance);

    /**
     * @param workspaceId authorized Workspace
     * @param instanceId stable Instance ID
     * @return matching Instance, if present
     */
    Optional<InstanceEntity> findById(String workspaceId, String instanceId);

    /**
     * Locks one Instance for mutually exclusive admission, cancel and dispatch.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId stable Instance ID
     * @return locked Instance when present
     */
    Optional<InstanceEntity> findByIdForUpdate(String workspaceId, String instanceId);

    /**
     * @param workspaceId authorized Workspace
     * @param trigger trigger namespace
     * @param requestId idempotency key
     * @return previously admitted Instance, if present
     */
    Optional<InstanceEntity> findByRequest(String workspaceId, TaskTriggerType trigger, String requestId);

    /**
     * @param workspaceId authorized Workspace
     * @param limit maximum recent Instances
     * @return recent Instances ordered by creation time
     */
    List<InstanceEntity> recent(String workspaceId, int limit);

    /**
     * @param workspaceId authorized Workspace
     * @param pageNo one-based page number
     * @param pageSize maximum page size
     * @return Workspace-owned Task Instance page
     */
    PageData<InstanceEntity> page(String workspaceId, long pageNo, long pageSize);

    /**
     * Transitions only an admitted SUBMITTED Instance to RUNNING.
     *
     * @param workspaceId owning Workspace
     * @param instanceId stable Instance ID
     * @return true when the transition was applied
     */
    boolean markRunning(String workspaceId, String instanceId);

    /**
     * Persists cancellation intent for a SUBMITTED or RUNNING Instance.
     *
     * @param workspaceId owning Workspace
     * @param instanceId stable Instance ID
     * @return true when the transition was applied
     */
    boolean markCancelling(String workspaceId, String instanceId);

    /**
     * Completes an active Instance with an authoritative outcome.
     *
     * @param workspaceId owning Workspace
     * @param instanceId stable Instance ID
     * @param status terminal business status
     * @param reason safe failure category, or null
     * @return true when the transition was applied
     */
    boolean markTerminal(String workspaceId, String instanceId, TaskInstanceStatus status, String reason);

    /**
     * Marks an uncertain, nonterminal Instance requiring ownership reconciliation.
     *
     * @param workspaceId owning Workspace
     * @param instanceId stable Instance ID
     * @return true when the transition was applied
     */
    boolean markReconciling(String workspaceId, String instanceId);
}
