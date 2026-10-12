package io.yak.ops.dao.repository.task;

import io.yak.ops.common.enums.task.TaskAttemptStatus;
import io.yak.ops.dao.entity.task.AttemptEntity;
import java.util.List;
import java.util.Optional;

/** Persists physical Task submissions and the durable single-dispatch fence. */
public interface AttemptRepository {

    /** @param attempt new physical submission bound to its owning Instance */
    void add(AttemptEntity attempt);

    /**
     * @param workspaceId authorized Workspace
     * @param instanceId owning logical run
     * @return Attempts ordered by ordinal
     */
    List<AttemptEntity> findByInstance(String workspaceId, String instanceId);

    /**
     * @param workspaceId authorized Workspace
     * @param attemptId physical submission identity
     * @return the matching Attempt when present
     */
    Optional<AttemptEntity> findById(String workspaceId, String attemptId);

    /**
     * Atomically transitions exactly one PENDING Attempt to LAUNCHING.
     *
     * @param workspaceId owning Workspace
     * @param attemptId Attempt to claim
     * @param ownerIncarnation verified embedded owner
     * @param dispatchToken unguessable one-launch token
     * @return true if the claimant won
     */
    boolean claimPending(String workspaceId, String attemptId, String ownerIncarnation, String dispatchToken);

    /**
     * Cancels a PENDING Attempt before it can launch.
     *
     * @param workspaceId owning Workspace
     * @param attemptId Attempt to cancel
     * @return true if cancellation won the dispatch fence
     */
    boolean cancelPending(String workspaceId, String attemptId);

    /**
     * Records the runtime reference after physical submission without overwriting terminal state.
     *
     * @param workspaceId owning Workspace
     * @param attemptId launched Attempt
     * @param dispatchToken token of the owning launcher
     * @param engine physical engine type
     * @param runtimeId engine-assigned Job ID
     * @return true when this launcher still owns the Attempt
     */
    boolean markRunning(String workspaceId, String attemptId, String dispatchToken, String engine, String runtimeId);

    /**
     * Persists one authoritative terminal execution result.
     *
     * @param workspaceId owning Workspace
     * @param attemptId launched Attempt
     * @param dispatchToken token of the owning launcher
     * @param status terminal Attempt status
     * @param reason safe failure category, or null
     * @return true when this launcher completed the Attempt
     */
    boolean markTerminal(
            String workspaceId, String attemptId, String dispatchToken, TaskAttemptStatus status, String reason);

    /**
     * Returns all unclaimed physical submissions eligible for safe dispatch recovery.
     *
     * <p>A PENDING Attempt cannot have launched a Job because its durable LAUNCHING
     * claim is the sole gateway to physical execution.
     *
     * @return unclaimed Attempts across Workspaces for the trusted embedded owner
     */
    List<AttemptEntity> pending();

    /**
     * @param ownerIncarnation previous owner's incarnation
     * @return physical executions that can no longer be observed by the current JVM
     */
    List<AttemptEntity> activeByOtherOwner(String ownerIncarnation);
}
