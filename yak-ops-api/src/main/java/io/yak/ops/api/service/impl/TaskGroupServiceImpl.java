package io.yak.ops.api.service.impl;

import io.yak.ops.api.constants.ApiFuncIdentificationConstant;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.ExecutorService;
import io.yak.ops.api.service.TaskGroupQueueService;
import io.yak.ops.api.service.TaskGroupService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.ProjectUser;
import io.yak.ops.dao.entity.TaskGroup;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.mapper.TaskGroupMapper;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.ProjectUserDao;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TaskGroupServiceImpl extends BaseServiceImpl implements TaskGroupService {

    @Autowired
    private TaskGroupMapper taskGroupMapper;

    @Autowired
    private ProjectDao projectDao;

    @Autowired
    private ProjectUserDao projectUserDao;

    @Autowired
    private TaskGroupQueueService taskGroupQueueService;

    @Autowired
    private ExecutorService executorService;

    @Override
    @Transactional
    public TaskGroup createTaskGroup(User loginUser, Long projectCode, String name, String description, int groupSize) {
        requireProjectPerm(loginUser, projectCode, true);
        if (checkDescriptionLength(description)) {
            log.warn("Parameter description is too long.");
            throw new ServiceException(Status.DESCRIPTION_TOO_LONG_ERROR);
        }
        if (name == null) {
            log.warn("Parameter name can ot be null.");
            throw new ServiceException(Status.NAME_NULL);
        }
        if (groupSize <= 0) {
            log.warn("Parameter task group size is must bigger than 1.");
            throw new ServiceException(Status.TASK_GROUP_SIZE_ERROR);
        }
        TaskGroup duplicate = taskGroupMapper.queryByName(loginUser.getId(), name);
        if (duplicate != null) {
            log.warn("Task group with the same name already exists, taskGroupName:{}.", duplicate.getName());
            throw new ServiceException(Status.TASK_GROUP_NAME_EXSIT);
        }
        Date now = new Date();
        TaskGroup taskGroup = TaskGroup.builder()
                .name(name)
                .projectCode(projectCode)
                .description(description)
                .groupSize(groupSize)
                .userId(loginUser.getId())
                .status(Flag.YES)
                .createTime(now)
                .updateTime(now)
                .build();

        if (taskGroupMapper.insert(taskGroup) <= 0) {
            log.error("Create task group error, taskGroupName:{}.", taskGroup.getName());
            throw new ServiceException(Status.CREATE_TASK_GROUP_ERROR);
        }
        log.info("Create task group complete, taskGroupName:{}.", taskGroup.getName());
        return taskGroup;
    }

    @Override
    public TaskGroup updateTaskGroup(User loginUser, int id, String name, String description, int groupSize) {
        TaskGroup taskGroup = taskGroupMapper.selectById(id);
        requireProjectPerm(loginUser, taskGroup.getProjectCode(), true);
        if (checkDescriptionLength(description)) {
            log.warn("Parameter description is too long.");
            throw new ServiceException(Status.DESCRIPTION_TOO_LONG_ERROR);
        }
        if (name == null) {
            log.warn("Parameter name can ot be null.");
            throw new ServiceException(Status.NAME_NULL);
        }
        if (groupSize <= 0) {
            log.warn("Parameter task group size is must bigger than 1.");
            throw new ServiceException(Status.TASK_GROUP_SIZE_ERROR);
        }
        Long exists = taskGroupMapper.selectCount(new QueryWrapper<TaskGroup>().lambda()
                .eq(TaskGroup::getName, name)
                .eq(TaskGroup::getUserId, loginUser.getId())
                .ne(TaskGroup::getId, id));

        if (exists > 0) {
            log.error("Task group with the same name already exists.");
            throw new ServiceException(Status.TASK_GROUP_NAME_EXSIT);
        }
        if (taskGroup.getStatus() != Flag.YES) {
            log.warn("Task group has been closed, taskGroupId:{}.", id);
            throw new ServiceException(Status.TASK_GROUP_STATUS_ERROR);
        }
        taskGroup.setGroupSize(groupSize);
        taskGroup.setDescription(description);
        taskGroup.setUpdateTime(new Date());
        if (StringUtils.isNotEmpty(name)) {
            taskGroup.setName(name);
        }
        if (taskGroupMapper.updateById(taskGroup) <= 0) {
            log.error("Update task group error, taskGroupId:{}.", id);
            throw new ServiceException(Status.UPDATE_TASK_GROUP_ERROR);
        }
        log.info("Update task group complete, taskGroupId:{}.", id);
        return taskGroup;
    }

    @Override
    public PageInfo<TaskGroup> queryAllTaskGroup(User loginUser, String name, Integer status, int pageNo,
                                                 int pageSize) {
        return this.doQuery(loginUser, pageNo, pageSize, loginUser.getId(), name, status);
    }

    @Override
    public PageInfo<TaskGroup> queryTaskGroupByStatus(User loginUser, int pageNo, int pageSize, int status) {
        return this.doQuery(loginUser, pageNo, pageSize, loginUser.getId(), null, status);
    }

    @Override
    public PageInfo<TaskGroup> queryTaskGroupByProjectCode(User loginUser, int pageNo, int pageSize, Long projectCode) {
        requireProjectPerm(loginUser, projectCode, false);
        Page<TaskGroup> page = new Page<>(pageNo, pageSize);
        IPage<TaskGroup> taskGroupPaging =
                taskGroupMapper.queryTaskGroupPagingByProjectCode(page, projectCode);

        return buildPageInfo(pageNo, pageSize, taskGroupPaging);
    }

    private PageInfo<TaskGroup> buildPageInfo(int pageNo, int pageSize, IPage<TaskGroup> taskGroupPaging) {
        PageInfo<TaskGroup> pageInfo = new PageInfo<>(pageNo, pageSize);
        int total = taskGroupPaging == null ? 0 : (int) taskGroupPaging.getTotal();
        List<TaskGroup> list = taskGroupPaging == null ? new ArrayList<TaskGroup>() : taskGroupPaging.getRecords();
        pageInfo.setTotal(total);
        pageInfo.setTotalList(list);
        return pageInfo;
    }

    @Override
    public TaskGroup queryTaskGroupById(User loginUser, int id) {
        return taskGroupMapper.selectById(id);
    }

    @Override
    public PageInfo<TaskGroup> doQuery(User loginUser, int pageNo, int pageSize, int userId, String name,
                                       Integer status) {
        Page<TaskGroup> page = new Page<>(pageNo, pageSize);
        IPage<TaskGroup> taskGroupPaging =
                taskGroupMapper.queryTaskGroupPaging(page, name, status);

        return buildPageInfo(pageNo, pageSize, taskGroupPaging);
    }

    @Override
    public void closeTaskGroup(User loginUser, int id) {
        if (!canOperatorPermissions(loginUser, null, AuthorizationType.TASK_GROUP,
                ApiFuncIdentificationConstant.TASK_GROUP_CLOSE)) {
            throw new ServiceException(Status.NO_CURRENT_OPERATING_PERMISSION);
        }
        TaskGroup taskGroup = taskGroupMapper.selectById(id);
        if (taskGroup.getStatus() == Flag.NO) {
            log.info("Task group has been closed, taskGroupId:{}.", id);
            throw new ServiceException(Status.TASK_GROUP_STATUS_CLOSED);
        }
        taskGroup.setStatus(Flag.NO);
        if (taskGroupMapper.updateById(taskGroup) > 0) {
            log.info("Task group close complete, taskGroupId:{}.", id);
        } else {
            log.error("Task group close error, taskGroupId:{}.", id);
        }
    }

    @Override
    public void startTaskGroup(User loginUser, int id) {
        if (!canOperatorPermissions(loginUser, null, AuthorizationType.TASK_GROUP,
                ApiFuncIdentificationConstant.TASK_GROUP_CLOSE)) {
            throw new ServiceException(Status.NO_CURRENT_OPERATING_PERMISSION);
        }
        TaskGroup taskGroup = taskGroupMapper.selectById(id);
        if (taskGroup.getStatus() == Flag.YES) {
            log.info("Task group has been started, taskGroupId:{}.", id);
            throw new ServiceException(Status.TASK_GROUP_STATUS_OPENED);
        }
        taskGroup.setStatus(Flag.YES);
        taskGroup.setUpdateTime(new Date(System.currentTimeMillis()));
        if (taskGroupMapper.updateById(taskGroup) > 0) {
            log.info("Task group start complete, taskGroupId:{}.", id);
        } else {
            log.error("Task group start error, taskGroupId:{}.", id);
        }
    }

    @Override
    public void forceStartTask(User loginUser, int queueId) {
        if (!canOperatorPermissions(loginUser, null, AuthorizationType.TASK_GROUP,
                ApiFuncIdentificationConstant.TASK_GROUP_QUEUE_START)) {
            throw new ServiceException(Status.NO_CURRENT_OPERATING_PERMISSION);
        }
        executorService.forceStartTaskInstance(loginUser, queueId);
    }

    @Override
    public void modifyPriority(User loginUser, Integer queueId, Integer priority) {
        if (!canOperatorPermissions(loginUser, null, AuthorizationType.TASK_GROUP,
                ApiFuncIdentificationConstant.TASK_GROUP_QUEUE_PRIORITY)) {
            throw new ServiceException(Status.NO_CURRENT_OPERATING_PERMISSION);
        }
        taskGroupQueueService.modifyPriority(queueId, priority);
        log.info("Modify task group queue priority complete, queueId:{}, priority:{}.", queueId, priority);
    }

    @Override
    public void deleteTaskGroupByProjectCode(long projectCode) {
        List<TaskGroup> taskGroups = taskGroupMapper.selectByProjectCode(projectCode);
        if (CollectionUtils.isEmpty(taskGroups)) {
            return;
        }
        List<Integer> taskGroupIds = taskGroups.stream()
                .map(TaskGroup::getId)
                .collect(Collectors.toList());
        taskGroupQueueService.deleteByTaskGroupIds(taskGroupIds);
        taskGroupMapper.deleteBatchIds(taskGroupIds);
    }

    private void requireProjectPerm(User loginUser, long projectCode, boolean writePermission) {
        if (loginUser.getUserType() == UserType.ADMIN_USER) {
            return;
        }
        Project project = projectDao.queryByCode(projectCode);
        if (project == null) {
            log.warn("Project does not exist, projectCode:{}.", projectCode);
            throw new ServiceException(Status.PROJECT_NOT_FOUND, projectCode);
        }
        if (project.getUserId().equals(loginUser.getId())) {
            return;
        }
        ProjectUser projectUser = projectUserDao.queryProjectRelation(project.getId(), loginUser.getId());
        if (projectUser == null) {
            log.warn("User {} does not have operation permission for project {}", loginUser.getUserName(),
                    project.getCode());
            throw new ServiceException(Status.USER_NO_OPERATION_PROJECT_PERM, loginUser.getUserName(),
                    project.getCode());
        }
        if (writePermission && projectUser.getPerm() != Constants.DEFAULT_ADMIN_PERMISSION) {
            log.warn("User {} does not have write permission for project {}", loginUser.getUserName(),
                    project.getCode());
            throw new ServiceException(Status.USER_NO_WRITE_PROJECT_PERM, loginUser.getUserName(),
                    project.getCode());
        }
    }
}
