package io.yak.ops.api.service.impl;

import io.yak.ops.api.service.TaskGroupQueueService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskGroupQueue;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.mapper.TaskGroupQueueMapper;
import io.yak.ops.dao.repository.ProjectDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TaskGroupQueueServiceImpl extends BaseServiceImpl implements TaskGroupQueueService {

    @Autowired
    TaskGroupQueueMapper taskGroupQueueMapper;

    @Autowired
    private ProjectDao projectDao;

    /**
     * query tasks in task group queue by group id
     *
     * @param loginUser login user
     * @param groupId   group id
     * @param pageNo    page no
     * @param pageSize  page size
     * @return tasks list
     */
    @Override
    public PageInfo<TaskGroupQueue> queryTasksByGroupId(User loginUser,
                                                        String taskName,
                                                        String workflowInstanceName,
                                                        Integer status,
                                                        int groupId,
                                                        int pageNo,
                                                        int pageSize) {
        PageInfo<TaskGroupQueue> pageInfo = new PageInfo<>(pageNo, pageSize);
        Set<Integer> projectIds = resourcePermissionCheckService
                .userOwnedResourceIdsAcquisition(AuthorizationType.PROJECTS, loginUser.getId(), log);
        if (projectIds.isEmpty()) {
            return pageInfo;
        }
        List<Project> projects = projectDao.queryByIds(projectIds);
        Page<TaskGroupQueue> page = new Page<>(pageNo, pageSize);
        IPage<TaskGroupQueue> taskGroupQueue = taskGroupQueueMapper.queryTaskGroupQueueByTaskGroupIdPaging(
                page,
                taskName,
                workflowInstanceName,
                status,
                groupId,
                projects);

        pageInfo.setTotal((int) taskGroupQueue.getTotal());
        pageInfo.setTotalList(taskGroupQueue.getRecords());
        return pageInfo;
    }

    @Override
    public void deleteByWorkflowInstanceId(Integer workflowInstanceId) {
        taskGroupQueueMapper.deleteByWorkflowInstanceId(workflowInstanceId);
    }

    @Override
    public void modifyPriority(Integer queueId, Integer priority) {
        taskGroupQueueMapper.modifyPriority(queueId, priority);
    }

    @Override
    public void deleteByTaskGroupIds(List<Integer> taskGroupIds) {
        if (CollectionUtils.isEmpty(taskGroupIds)) {
            return;
        }
        taskGroupQueueMapper.deleteByTaskGroupIds(taskGroupIds);
    }
}
