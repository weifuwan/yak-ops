package io.yak.ops.dao.repository;

import io.yak.ops.common.enums.TaskGroupQueueStatus;
import io.yak.ops.dao.entity.TaskGroupQueue;
import io.yak.ops.dao.entity.WorkflowInstance;

import java.util.Date;
import java.util.List;

public interface TaskGroupQueueDao extends IDao<TaskGroupQueue> {

    /**
     * Delete {@link TaskGroupQueue} by {@link WorkflowInstance#getId()}
     *
     * @param workflowInstanceIds workflowInstanceIds
     */
    void deleteByWorkflowInstanceIds(List<Integer> workflowInstanceIds);

    /**
     * Query all {@link TaskGroupQueue} which in_queue is {@link io.yak.ops.common.enums.Flag#YES}
     *
     * @return TaskGroupQueue ordered by priority desc
     */
    List<TaskGroupQueue> queryAllInQueueTaskGroupQueue();

    /**
     * Query all {@link TaskGroupQueue} which
     * in_queue is {@link io.yak.ops.common.enums.Flag#YES}
     * and id > minTaskGroupQueueId
     * ordered by id asc
     * limit #{limit}
     *
     * @return TaskGroupQueue ordered by id asc
     */
    List<TaskGroupQueue> queryInQueueTaskGroupQueue(int minTaskGroupQueueId, int limit);

    /**
     * Query all {@link TaskGroupQueue} which in_queue is {@link io.yak.ops.common.enums.Flag#YES} and taskGroupId is taskGroupId
     *
     * @param taskGroupId taskGroupId
     * @return TaskGroupQueue ordered by priority desc
     */
    List<TaskGroupQueue> queryAllInQueueTaskGroupQueueByGroupId(Integer taskGroupId);

    /**
     * Query all {@link TaskGroupQueue} which taskId is taskInstanceId
     *
     * @param taskInstanceId taskInstanceId
     * @return TaskGroupQueue ordered by priority desc
     */
    List<TaskGroupQueue> queryByTaskInstanceId(Integer taskInstanceId);

    /**
     * Atomically change a waiting TaskGroupQueue to acquired.
     *
     * @param id taskGroupQueue id
     * @param updateTime update time
     * @return true if the waiting TaskGroupQueue was acquired
     */
    boolean acquireTaskGroupQueue(Integer id, Date updateTime);

    /**
     * Delete the TaskGroupQueue only when its current status matches the expected status.
     *
     * @param taskInstanceId taskInstance id
     * @param status expected status
     * @return true if a TaskGroupQueue was deleted
     */
    boolean deleteByTaskInstanceIdAndStatus(Integer taskInstanceId, TaskGroupQueueStatus status);

    /**
     * Query all {@link TaskGroupQueue} which status is TaskGroupQueueStatus.ACQUIRE_SUCCESS and forceStart is {@link io.yak.ops.common.enums.Flag#NO}.
     *
     * @param taskGroupId taskGroupId
     * @return TaskGroupQueue
     */
    List<TaskGroupQueue> queryAcquiredTaskGroupQueueByGroupId(Integer taskGroupId);

    /**
     * Count all {@link TaskGroupQueue} which status is TaskGroupQueueStatus.ACQUIRE_SUCCESS and forceStart is {@link io.yak.ops.common.enums.Flag#NO}.
     *
     * @param taskGroupId taskGroupId
     * @return TaskGroupQueue
     */
    int countUsingTaskGroupQueueByGroupId(Integer taskGroupId);

    /**
     * Query all {@link TaskGroupQueue} which
     * in_queue is {@link io.yak.ops.common.enums.Flag#YES}
     * and forceStart is {@link io.yak.ops.common.enums.Flag#YES}
     * and id > minTaskGroupQueueId
     * order by id asc
     * limit #{limit}
     *
     * @return TaskGroupQueue ordered by priority desc
     */
    List<TaskGroupQueue> queryWaitNotifyForceStartTaskGroupQueue(int minTaskGroupQueueId, int limit);
}
