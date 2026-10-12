package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.AttemptEntity;
import java.util.List;

/** Persists physical Task submissions and guards dispatch ownership. */
public interface AttemptRepository {

    /**
     * Inserts the initial PENDING physical submission for an Instance.
     *
     * @param attempt submission associated with its owning Instance
     */
    void add(AttemptEntity attempt);

    /**
     * Lists physical submissions within their owning Workspace.
     *
     * @param workspaceId authorized Workspace identifier
     * @param instanceId logical run identifier
     * @return Attempts ordered by ascending submission number
     */
    List<AttemptEntity> findByInstance(String workspaceId, String instanceId);

    /**
     * Atomically changes one PENDING Attempt to LAUNCHING.
     *
     * <p>The caller must also enforce the cancellation fence and verified process
     * ownership before launching external execution.
     *
     * @param workspaceId authorized Workspace identifier
     * @param attemptId physical submission to claim
     * @param ownerIncarnation unique embedded executor incarnation
     * @param dispatchToken opaque identity of the successful dispatch claim
     * @return true only if one matching PENDING row was claimed
     */
    boolean claimPending(String workspaceId, String attemptId, String ownerIncarnation, String dispatchToken);
}
