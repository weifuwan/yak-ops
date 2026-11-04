package io.yak.ops.api.service.impl;

import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.DOWNLOAD_LOG;
import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.VIEW_LOG;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.executor.logging.LogClientDelegate;
import io.yak.ops.api.service.LoggerService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.ResponseTaskLog;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskInstanceDao;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.primitives.Bytes;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LoggerServiceImpl extends BaseServiceImpl implements LoggerService {

    private static final String LOG_HEAD_FORMAT = "[LOG-PATH]: %s, [HOST]: %s%s";

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Autowired
    private ProjectDao projectDao;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskDefinitionDao taskDefinitionDao;

    @Autowired
    private LogClientDelegate logClientDelegate;

    /**
     * view log
     *
     * @param loginUser   login user
     * @param taskInstId  task instance id
     * @param skipLineNum skip line number
     * @param limit       limit
     * @return log string data
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result<ResponseTaskLog> queryLog(User loginUser, int taskInstId, int skipLineNum, int limit) {

        TaskInstance taskInstance = taskInstanceDao.queryById(taskInstId);

        if (taskInstance == null) {
            log.error("Task instance does not exist, taskInstanceId:{}.", taskInstId);
            return Result.error(Status.TASK_INSTANCE_NOT_FOUND);
        }
        if (StringUtils.isBlank(taskInstance.getHost())) {
            log.error("Host of task instance is null, taskInstanceId:{}.", taskInstId);
            return Result.error(Status.TASK_INSTANCE_HOST_IS_NULL);
        }
        projectService.checkProjectAndAuthThrowException(loginUser, taskInstance.getProjectCode(), VIEW_LOG);
        Result<ResponseTaskLog> result = new Result<>(Status.SUCCESS.getCode(), Status.SUCCESS.getMsg());
        String log = queryLog(taskInstance, skipLineNum, limit);
        int lineNum = log.split("\\r\\n").length;
        result.setData(new ResponseTaskLog(lineNum, log));
        return result;
    }

    /**
     * get log size
     *
     * @param loginUser  login user
     * @param taskInstId task instance id
     * @return log byte array
     */
    @Override
    public byte[] getLogBytes(User loginUser, int taskInstId) {
        TaskInstance taskInstance = taskInstanceDao.queryById(taskInstId);
        if (taskInstance == null || StringUtils.isBlank(taskInstance.getHost())) {
            throw new ServiceException("task instance is null or host is null");
        }
        Project project = projectDao.queryProjectByTaskInstanceId(taskInstId);
        projectService.checkProjectAndAuthThrowException(loginUser, project, DOWNLOAD_LOG);
        return getLogBytes(taskInstance);
    }

    /**
     * query log
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param taskInstId  task instance id
     * @param skipLineNum skip line number
     * @param limit       limit
     * @return log string data
     */
    @Override
    @SuppressWarnings("unchecked")
    public String queryLog(User loginUser, long projectCode, int taskInstId, int skipLineNum, int limit) {
        // check user access for project
        projectService.checkProjectAndAuthThrowException(loginUser, projectCode, VIEW_LOG);
        // check whether the task instance can be found
        TaskInstance task = taskInstanceDao.queryById(taskInstId);
        if (task == null || StringUtils.isBlank(task.getHost())) {
            throw new ServiceException(Status.TASK_INSTANCE_NOT_FOUND);
        }

        TaskDefinition taskDefinition = taskDefinitionDao.queryByCode(task.getTaskCode());
        if (taskDefinition != null && projectCode != taskDefinition.getProjectCode()) {
            throw new ServiceException(Status.TASK_INSTANCE_NOT_FOUND, taskInstId);
        }
        return queryLog(task, skipLineNum, limit);
    }

    /**
     * get log bytes
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param taskInstId  task instance id
     * @return log byte array
     */
    @Override
    public byte[] getLogBytes(User loginUser, long projectCode, int taskInstId) {
        // check user access for project
        projectService.checkProjectAndAuthThrowException(loginUser, projectCode, DOWNLOAD_LOG);

        // check whether the task instance can be found
        TaskInstance task = taskInstanceDao.queryById(taskInstId);
        if (task == null || StringUtils.isBlank(task.getHost())) {
            throw new ServiceException("task instance is null or host is null");
        }

        TaskDefinition taskDefinition = taskDefinitionDao.queryByCode(task.getTaskCode());
        if (taskDefinition != null && projectCode != taskDefinition.getProjectCode()) {
            throw new ServiceException("task instance does not exist in project");
        }
        return getLogBytes(task);
    }

    /**
     * query log
     *
     * @param taskInstance task instance
     * @param skipLineNum  skip line number
     * @param limit        limit
     * @return log string data
     */
    private String queryLog(TaskInstance taskInstance, int skipLineNum, int limit) {
        final String logPath = taskInstance.getLogPath();
        log.info("Query task instance log, taskInstanceId:{}, taskInstanceName:{}, host: {}, logPath:{}",
                taskInstance.getId(), taskInstance.getName(), taskInstance.getHost(), logPath);
        if (StringUtils.isBlank(logPath)) {
            throw new ServiceException(Status.QUERY_TASK_INSTANCE_LOG_ERROR,
                    "TaskInstanceLogPath is empty, maybe the taskInstance doesn't be dispatched");
        }

        StringBuilder sb = new StringBuilder();
        if (skipLineNum == 0) {
            String head = String.format(LOG_HEAD_FORMAT,
                    logPath,
                    taskInstance.getHost(),
                    Constants.SYSTEM_LINE_SEPARATOR);
            sb.append(head);
        }

        try {
            String logContent = logClientDelegate.getPartLogString(taskInstance, skipLineNum, limit);
            if (logContent != null) {
                sb.append(logContent);
            }
            return sb.toString();
        } catch (Throwable ex) {
            throw new ServiceException(Status.QUERY_TASK_INSTANCE_LOG_ERROR, ex.getMessage(), ex);
        }
    }

    /**
     * get log bytes
     *
     * @param taskInstance task instance
     * @return log byte array
     */
    private byte[] getLogBytes(TaskInstance taskInstance) {
        String host = taskInstance.getHost();
        String logPath = taskInstance.getLogPath();

        byte[] head = String.format(LOG_HEAD_FORMAT,
                logPath,
                host,
                Constants.SYSTEM_LINE_SEPARATOR).getBytes(StandardCharsets.UTF_8);

        byte[] logBytes;

        try {
            logBytes = logClientDelegate.getWholeLogBytes(taskInstance);
            return Bytes.concat(head, logBytes);
        } catch (Exception ex) {
            log.error("Download TaskInstance: {} Log Error", taskInstance.getName(), ex);
            throw new ServiceException(Status.DOWNLOAD_TASK_INSTANCE_LOG_FILE_ERROR);
        }
    }
}
