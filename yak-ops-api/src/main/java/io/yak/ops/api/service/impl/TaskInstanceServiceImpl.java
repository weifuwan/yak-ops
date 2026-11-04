package io.yak.ops.api.service.impl;

import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.TASK_INSTANCE;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.service.TaskGroupQueueService;
import io.yak.ops.api.service.TaskInstanceService;
import io.yak.ops.api.service.UsersService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.common.ILogService;
import io.yak.ops.extract.worker.IPhysicalTaskExecutorOperator;
import io.yak.ops.extract.worker.IStreamingTaskInstanceOperator;
import io.yak.ops.extract.worker.transportor.TaskInstanceTriggerSavepointRequest;
import io.yak.ops.extract.worker.transportor.TaskInstanceTriggerSavepointResponse;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.service.process.ProcessService;
import io.yak.ops.task.executor.operations.TaskExecutorKillRequest;
import io.yak.ops.task.executor.operations.TaskExecutorKillResponse;

import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TaskInstanceServiceImpl extends BaseServiceImpl implements TaskInstanceService {

    @Autowired
    ProjectDao projectDao;

    @Autowired
    ProjectService projectService;

    @Autowired
    ProcessService processService;

    @Autowired
    TaskInstanceDao taskInstanceDao;

    @Autowired
    UsersService usersService;

    @Autowired
    private TaskGroupQueueService taskGroupQueueService;

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    /**
     * query task list by project, workflow instance, task name, task start time, task end time, task status, keyword paging
     *
     * @param loginUser         login user
     * @param projectCode       project code
     * @param workflowInstanceId workflow instance id
     * @param searchVal         search value
     * @param taskName          task name
     * @param taskCode          task code
     * @param stateType         state type
     * @param host              host
     * @param startDate         start time
     * @param endDate           end time
     * @param pageNo            page number
     * @param pageSize          page size
     * @return task list page
     */
    @Override
    public Result queryTaskListPaging(User loginUser,
                                      long projectCode,
                                      Integer workflowInstanceId,
                                      String workflowInstanceName,
                                      String workflowDefinitionName,
                                      String taskName,
                                      Long taskCode,
                                      String executorName,
                                      String startDate,
                                      String endDate,
                                      String searchVal,
                                      TaskExecutionStatus stateType,
                                      String host,
                                      TaskExecuteType taskExecuteType,
                                      Integer pageNo,
                                      Integer pageSize) {
        Result result = new Result();
        // check user access for project
        projectService.checkProjectAndAuthThrowException(loginUser, projectCode, TASK_INSTANCE);
        int[] statusArray = null;
        if (stateType != null) {
            statusArray = new int[]{stateType.getCode()};
        }
        Date start = checkAndParseDateParameters(startDate);
        Date end = checkAndParseDateParameters(endDate);
        Page<TaskInstance> page = new Page<>(pageNo, pageSize);
        PageInfo<TaskInstance> pageInfo = new PageInfo<>(pageNo, pageSize);
        IPage<TaskInstance> taskInstanceIPage;
        if (taskExecuteType == TaskExecuteType.STREAM) {
            // stream task without workflow instance
            taskInstanceIPage = taskInstanceDao.queryStreamTaskInstanceListPaging(
                    page,
                    projectCode,
                    workflowDefinitionName,
                    searchVal,
                    taskName,
                    taskCode,
                    executorName,
                    statusArray,
                    host,
                    taskExecuteType,
                    start,
                    end);
        } else {
            taskInstanceIPage = taskInstanceDao.queryTaskInstanceListPaging(
                    page,
                    projectCode,
                    workflowInstanceId,
                    workflowInstanceName,
                    searchVal,
                    taskName,
                    taskCode,
                    executorName,
                    statusArray,
                    host,
                    taskExecuteType,
                    start,
                    end);
        }
        List<TaskInstance> taskInstanceList = taskInstanceIPage.getRecords();
        List<Integer> executorIds =
                taskInstanceList.stream().map(TaskInstance::getExecutorId).distinct().collect(Collectors.toList());
        List<User> users = usersService.queryUser(executorIds);
        Map<Integer, User> userMap = users.stream().collect(Collectors.toMap(User::getId, v -> v));
        for (TaskInstance taskInstance : taskInstanceList) {
            taskInstance.setDuration(DateUtils.format2Duration(taskInstance.getStartTime(), taskInstance.getEndTime()));
            User user = userMap.get(taskInstance.getExecutorId());
            if (user != null) {
                taskInstance.setExecutorName(user.getUserName());
            }
        }
        pageInfo.setTotal((int) taskInstanceIPage.getTotal());
        pageInfo.setTotalList(taskInstanceList);
        result.setData(pageInfo);
        putMsg(result, Status.SUCCESS);
        return result;
    }

    /**
     * change one task instance's state from failure to forced success
     *
     * @param loginUser      login user
     * @param projectCode    project code
     * @param taskInstanceId task instance id
     * @return the result code and msg
     */
    @Transactional
    @Override
    public void forceTaskSuccess(User loginUser, long projectCode, Integer taskInstanceId) {
        projectService.checkHasProjectWritePermissionThrowException(loginUser, projectCode);

        TaskInstance task = taskInstanceDao.queryOptionalById(taskInstanceId)
                .orElseThrow(() -> new ServiceException(Status.TASK_INSTANCE_NOT_FOUND));

        if (task.getProjectCode() != projectCode) {
            throw new ServiceException("The task instance is not under the project: " + projectCode);
        }

        WorkflowInstance workflowInstance = workflowInstanceDao.queryOptionalById(task.getWorkflowInstanceId())
                .orElseThrow(
                        () -> new ServiceException(Status.WORKFLOW_INSTANCE_NOT_EXIST, task.getWorkflowInstanceId()));
        if (!workflowInstance.getState().isFinalState()) {
            throw new ServiceException("The workflow instance is not finished: " + workflowInstance.getState()
                    + " cannot force start task instance");
        }

        // check whether the task instance state type is failure or cancel
        if (!task.getState().isFailure() && !task.getState().isKill()) {
            throw new ServiceException(Status.TASK_INSTANCE_STATE_OPERATION_ERROR, taskInstanceId, task.getState());
        }

        // change the state of the task instance
        task.setState(TaskExecutionStatus.FORCED_SUCCESS);
        task.setEndTime(new Date());
        boolean changed = taskInstanceDao.updateById(task);
        if (!changed) {
            throw new ServiceException(Status.FORCE_TASK_SUCCESS_ERROR);
        }
        processService.forceWorkflowInstanceSuccessByTaskInstanceId(task);
        log.info("Force success task instance id: {} success", taskInstanceId);
    }

    @Override
    public Result taskSavePoint(User loginUser, long projectCode, Integer taskInstanceId) {
        Result result = new Result();

        Project project = projectDao.queryByCode(projectCode);
        projectService.checkHasProjectWritePermissionThrowException(loginUser, project);

        TaskInstance taskInstance = taskInstanceDao.queryById(taskInstanceId);
        if (taskInstance == null || taskInstance.getProjectCode() != projectCode) {
            log.error("Task definition can not be found, projectCode:{}, taskInstanceId:{}.", projectCode,
                    taskInstanceId);
            putMsg(result, Status.TASK_INSTANCE_NOT_FOUND);
            return result;
        }
        final TaskInstanceTriggerSavepointResponse taskInstanceTriggerSavepointResponse = Clients
                .withService(IStreamingTaskInstanceOperator.class)
                .withHost(taskInstance.getHost())
                .triggerSavepoint(new TaskInstanceTriggerSavepointRequest(taskInstanceId));
        log.info("StreamingTaskInstance trigger savepoint response: {}", taskInstanceTriggerSavepointResponse);
        putMsg(result, Status.SUCCESS);
        return result;
    }

    @Override
    public Result stopTask(User loginUser, long projectCode, Integer taskInstanceId) {
        Result result = new Result();

        Project project = projectDao.queryByCode(projectCode);
        projectService.checkHasProjectWritePermissionThrowException(loginUser, project);

        TaskInstance taskInstance = taskInstanceDao.queryById(taskInstanceId);
        if (taskInstance == null || taskInstance.getProjectCode() != projectCode) {
            log.error("Task definition can not be found, projectCode:{}, taskInstanceId:{}.", projectCode,
                    taskInstanceId);
            putMsg(result, Status.TASK_INSTANCE_NOT_FOUND);
            return result;
        }

        // todo: we only support streaming task for now
        final TaskExecutorKillResponse taskExecutorKillResponse = Clients
                .withService(IPhysicalTaskExecutorOperator.class)
                .withHost(taskInstance.getHost())
                .killTask(TaskExecutorKillRequest.of(taskInstanceId));
        log.info("TaskInstance kill response: {}", taskExecutorKillResponse);

        putMsg(result, Status.SUCCESS);
        return result;
    }

    @Override
    public void deleteByWorkflowInstanceId(Integer workflowInstanceId) {
        List<TaskInstance> needToDeleteTaskInstances =
                taskInstanceDao.queryByWorkflowInstanceId(workflowInstanceId);
        if (org.apache.commons.collections4.CollectionUtils.isEmpty(needToDeleteTaskInstances)) {
            return;
        }
        for (TaskInstance taskInstance : needToDeleteTaskInstances) {
            if (StringUtils.isNotBlank(taskInstance.getLogPath())) {
                try {
                    // Remove task instance log failed will not affect the deletion of task instance
                    Clients
                            .withService(ILogService.class)
                            .withHost(taskInstance.getHost())
                            .removeTaskInstanceLog(taskInstance.getLogPath());
                } catch (Exception ex) {
                    log.error("Remove task instance log error", ex);
                }
            }
        }

        taskGroupQueueService.deleteByWorkflowInstanceId(workflowInstanceId);
        taskInstanceDao.deleteByWorkflowInstanceId(workflowInstanceId);
    }

}
