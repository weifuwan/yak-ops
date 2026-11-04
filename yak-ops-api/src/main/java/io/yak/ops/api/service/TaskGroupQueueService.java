package io.yak.ops.api.service;

import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.dao.entity.TaskGroupQueue;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface TaskGroupQueueService {

    /**
     * query tasks in task group queue by group id
     * @param loginUser   login user
     * @param groupId     group id
     * @param taskName    Task Name
     * @param workflowInstanceName workflow instance name
     * @param status      Task queue status
     * @param pageNo      page no
     * @param pageSize    page size
     * @return tasks list page
     */
    PageInfo<TaskGroupQueue> queryTasksByGroupId(User loginUser, String taskName, String workflowInstanceName,
                                                 Integer status,
                                                 int groupId, int pageNo, int pageSize);

    void deleteByWorkflowInstanceId(Integer workflowInstanceId);

    void modifyPriority(Integer queueId, Integer priority);

    void deleteByTaskGroupIds(List<Integer> taskGroupIds);
}
