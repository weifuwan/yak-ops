package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.ExecutionClaimEntity;

/** Owns database-enforced exclusive execution resources. */
public interface ExecutionClaimRepository {

    /**
     * Acquires one canonical resource, rejecting conflicting owners by unique key.
     *
     * @param claim resource and owning Instance persisted during admission
     */
    void add(ExecutionClaimEntity claim);

    /**
     * Releases claims belonging to one conclusively terminal Instance.
     *
     * <p>The caller must prove external writes have stopped and release claims
     * atomically with the Instance terminal transition.
     *
     * @param workspaceId authorized Workspace identifier
     * @param instanceId Instance that currently owns the claims
     * @return number of resource claims removed for the owner
     */
    int releaseOwned(String workspaceId, String instanceId);
}
