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

    Optional<TaskGroupQueueEntity> findResourceForUpdate(String workspaceId, String type, String key);

    List<TaskGroupQueueEntity> findByInstance(String workspaceId, String instanceId);

    int releaseOwned(String workspaceId, String instanceId);
}
