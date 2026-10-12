package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import java.util.List;

/** Owns database-enforced exclusive execution resources. */
public interface ExecutionClaimRepository {

    /** @param claim canonical exclusive claim to persist during admission */
    void add(ExecutionClaimEntity claim);

    /**
     * @param workspaceId authorized Workspace
     * @param instanceId owning Instance
     * @return canonical resource claims held by the Instance
     */
    List<ExecutionClaimEntity> findByInstance(String workspaceId, String instanceId);

    /**
     * Releases claims after authoritative terminal completion and writer quiescence.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId conclusively terminal claim owner
     * @return number of released claims
     */
    int releaseOwned(String workspaceId, String instanceId);
}
