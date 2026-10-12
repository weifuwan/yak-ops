package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.ExecutionClaimEntity;

/** Acquires exclusive resources using database uniqueness constraints. */
public interface ExecutionClaimRepository {

    void add(ExecutionClaimEntity claim);

    /** Releases only claims belonging to the confirmed terminal Instance. */
    int releaseOwned(String workspaceId, String instanceId);
}
