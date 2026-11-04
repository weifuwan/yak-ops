package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.TaskGroup;

import java.util.List;

public interface TaskGroupDao extends IDao<TaskGroup> {

    /**
     * Query all TaskGroups
     *
     * @return all TaskGroups
     */
    List<TaskGroup> queryAllTaskGroups();

    /**
     * Query all TaskGroups which useSize > 0
     *
     * @return the TaskGroups which useSize > 0
     */
    List<TaskGroup> queryUsedTaskGroups();

    /**
     * Query all TaskGroups which useSize < groupSize
     *
     * @return the TaskGroups which useSize < groupSize
     */
    List<TaskGroup> queryAvailableTaskGroups();

    /**
     * Acquire a slot for the TaskGroup which useSize should < groupSize, set the useSize = useSize + 1.
     *
     * @param taskGroupId taskGroupId which shouldn't be null
     * @return true if acquire successfully, false otherwise.
     */
    boolean acquireTaskGroupSlot(Integer taskGroupId);

    /**
     * Release a slot for the TaskGroup which useSize should > 0, set the useSize = useSize - 1.
     *
     * @param taskGroupId taskGroupId which shouldn't be null
     * @return true if release successfully, false otherwise.
     */
    boolean releaseTaskGroupSlot(Integer taskGroupId);
}
