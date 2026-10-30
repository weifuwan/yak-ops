package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import java.util.List;
import java.util.Optional;

/**
 * DolphinScheduler TaskGroup slots, adapted to string Task IDs and atomically
 * acquired multi-resource executions. Callers hold the Instance transaction.
 */
public interface TaskGroupQueueRepository {
    void add(TaskGroupQueueEntity queue);

    /** Atomically grants all waiting resource slots to a SUBMITTED Instance, if eligible. */
    boolean tryAcquire(String workspaceId, String instanceId);

    /** Whether no resource is waiting for acquisition. */
    boolean allAcquired(String workspaceId, String instanceId);

    /**
     * Lists the next waiting page using the stable priority/time/id ordering.
     * A null cursor starts from the highest-priority entry.
     */
    List<TaskGroupQueueEntity> waiting(int limit, TaskGroupQueueEntity after);

    /** Reconciles capacity counters against durable acquired rows, preserving uncertain runs. */
    void reconcile();

    Optional<TaskGroupQueueEntity> findResourceForUpdate(String workspaceId, String type, String key);

    List<TaskGroupQueueEntity> findByInstance(String workspaceId, String instanceId);

    int releaseOwned(String workspaceId, String instanceId);
}
