package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.AttemptEntity;
import java.util.List;

/** Task Attempt persistence with an atomic single-dispatch admission fence. */
public interface AttemptRepository {

    void add(AttemptEntity attempt);

    List<AttemptEntity> findByInstance(String workspaceId, String instanceId);

    /** Claims PENDING exactly once; the caller must verify the matching row count. */
    boolean claimPending(String workspaceId, String attemptId, String ownerIncarnation, String dispatchToken);
}
