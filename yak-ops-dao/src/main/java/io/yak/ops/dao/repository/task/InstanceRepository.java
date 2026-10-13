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
     * Looks up an Instance for internal terminal log-file retention checks.
     *
     * @param instanceId stable Instance ID
     * @return persisted Instance if present
     */
    Optional<InstanceEntity> findByIdForLogCleanup(String instanceId);

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
     * @param definitionId optional Definition filter
     * @param status optional lifecycle filter
     * @param taskType optional registered plugin type
     * @return Workspace-owned Task Instance page
     */
    PageData<InstanceEntity> page(
            String workspaceId,
            long pageNo,
            long pageSize,
            String definitionId,
            TaskInstanceStatus status,
            String taskType);

    /** @return committed Task Instances awaiting their first physical submission */
    List<InstanceEntity> pending();

    /**
     * @param ownerIncarnation current embedded execution owner
     * @return active Instances owned by a previous JVM
     */
    List<InstanceEntity> activeByOtherOwner(String ownerIncarnation);

    /**
     * Claims a SUBMITTED Instance before starting a physical Job.
     *
     * @param workspaceId owning Workspace
     * @param instanceId pending Instance
     * @param ownerIncarnation verified embedded owner
     * @param dispatchToken unique one-time launch token
     * @return true when this claimant won
     */
    boolean claim(String workspaceId, String instanceId, String ownerIncarnation, String dispatchToken);

    /**
     * Persists the Job identity while retaining a concurrent cancellation request.
     *
     * @param workspaceId owning Workspace
     * @param instanceId launched Instance
     * @param dispatchToken winning launcher token
     * @param engine physical execution engine
     * @param runtimeId physical Job identifier
     * @return true when the launch is still owned by the caller
     */
    boolean markRunning(String workspaceId, String instanceId, String dispatchToken, String engine, String runtimeId);

    /**
     * Atomically cancels a SUBMITTED Instance before dispatch.
     *
     * @param workspaceId owning Workspace
     * @param instanceId pending Instance
     * @return true if cancellation won
     */
    boolean cancelSubmitted(String workspaceId, String instanceId);

    /**
     * Records cancellation intent for claimed work.
     *
     * @param workspaceId owning Workspace
     * @param instanceId launched Instance
     * @return true when cancellation was recorded
     */
    boolean markCancelling(String workspaceId, String instanceId);

    /**
     * Persists an authoritative terminal outcome under the owning dispatch token.
     *
     * @param workspaceId owning Workspace
     * @param instanceId launched Instance
     * @param dispatchToken winning launch token
     * @param status terminal Instance status
     * @param reason safe failure category or null
     * @return true when terminal outcome was recorded
     */
    boolean markTerminal(
            String workspaceId, String instanceId, String dispatchToken, TaskInstanceStatus status, String reason);

    /**
     * Preserves resource claims for a physically uncertain submission.
     *
     * @param workspaceId owning Workspace
     * @param instanceId launched Instance
     * @param dispatchToken original launch token
     * @return true if reconciliation was recorded
     */
    boolean markReconciling(String workspaceId, String instanceId, String dispatchToken);
}
