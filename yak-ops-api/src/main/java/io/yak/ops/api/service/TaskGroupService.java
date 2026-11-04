package io.yak.ops.api.service;

import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.dao.entity.TaskGroup;
import io.yak.ops.dao.entity.User;

public interface TaskGroupService {

    /**
     * create a Task group
     */
    TaskGroup createTaskGroup(User loginUser, Long projectCode, String name, String description, int groupSize);

    /**
     * update the task group
     */
    TaskGroup updateTaskGroup(User loginUser, int id, String name, String description, int groupSize);

    /**
     * query all task group by user id
     */
    PageInfo<TaskGroup> queryAllTaskGroup(User loginUser, String name, Integer status, int pageNo, int pageSize);

    /**
     * query all task group by status
     */
    PageInfo<TaskGroup> queryTaskGroupByStatus(User loginUser, int pageNo, int pageSize, int status);

    /**
     * query all task group by project code
     */
    PageInfo<TaskGroup> queryTaskGroupByProjectCode(User loginUser, int pageNo, int pageSize, Long projectCode);

    /**
     * query task group by id (returns null if not found)
     */
    TaskGroup queryTaskGroupById(User loginUser, int id);

    /**
     * paginated query of task groups
     */
    PageInfo<TaskGroup> doQuery(User loginUser, int pageNo, int pageSize, int userId, String name, Integer status);

    /**
     * close a task group
     */
    void closeTaskGroup(User loginUser, int id);

    /**
     * start a task group
     */
    void startTaskGroup(User loginUser, int id);

    /**
     * wake a task manually
     */
    void forceStartTask(User loginUser, int taskId);

    void modifyPriority(User loginUser, Integer queueId, Integer priority);

    void deleteTaskGroupByProjectCode(long projectCode);
}
