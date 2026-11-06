package io.yak.ops.api.service;

import static io.yak.ops.api.AssertionsHelper.assertDoesNotThrow;
import static io.yak.ops.api.AssertionsHelper.assertThrowsServiceException;
import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.DOWNLOAD_LOG;
import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.VIEW_LOG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import io.yak.ops.api.AssertionsHelper;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.executor.logging.LogClientDelegate;
import io.yak.ops.api.service.impl.LoggerServiceImpl;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.ResponseTaskLog;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskInstanceDao;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LoggerServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(LoggerServiceTest.class);

    @InjectMocks
    private LoggerServiceImpl loggerService;

    @Mock
    private TaskInstanceDao taskInstanceDao;

    @Mock
    private ProjectDao projectDao;

    @Mock
    private ProjectService projectService;

    @Mock
    private TaskDefinitionDao taskDefinitionDao;

    @Mock
    private LogClientDelegate logClientDelegate;

    private final int nettyServerPort = 18080;

    @Test
    public void testQueryLog() {

        User loginUser = new User();
        loginUser.setId(1);
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setExecutorId(loginUser.getId() + 1);
        when(taskInstanceDao.queryById(1)).thenReturn(taskInstance);
        Result result = loggerService.queryLog(loginUser, 2, 1, 1);
        // TASK_INSTANCE_NOT_FOUND
        Assertions.assertEquals(Status.TASK_INSTANCE_NOT_FOUND.getCode(), result.getCode().intValue());

        try {
            // HOST NOT FOUND OR ILLEGAL
            result = loggerService.queryLog(loginUser, 1, 1, 1);
        } catch (RuntimeException e) {
            Assertions.assertTrue(true);
            logger.error("testQueryDataSourceList error {}", e.getMessage());
        }
        Assertions.assertEquals(Status.TASK_INSTANCE_HOST_IS_NULL.getCode(), result.getCode().intValue());

        // PROJECT_NOT_EXIST
        taskInstance.setHost("127.0.0.1:" + nettyServerPort);
        taskInstance.setLogPath("/temp/log");
        doThrow(new ServiceException(Status.PROJECT_NOT_EXIST)).when(projectService)
                .checkProjectAndAuthThrowException(loginUser, taskInstance.getProjectCode(), VIEW_LOG);
        AssertionsHelper.assertThrowsServiceException(Status.PROJECT_NOT_EXIST,
                () -> loggerService.queryLog(loginUser, 1, 1, 1));

        // USER_NO_OPERATION_PERM
        doThrow(new ServiceException(Status.USER_NO_OPERATION_PERM)).when(projectService)
                .checkProjectAndAuthThrowException(loginUser, taskInstance.getProjectCode(), VIEW_LOG);
        AssertionsHelper.assertThrowsServiceException(Status.USER_NO_OPERATION_PERM,
                () -> loggerService.queryLog(loginUser, 1, 1, 1));

        // SUCCESS
        doNothing().when(projectService).checkProjectAndAuthThrowException(loginUser, taskInstance.getProjectCode(),
                VIEW_LOG);
        when(taskInstanceDao.queryById(1)).thenReturn(taskInstance);
        result = loggerService.queryLog(loginUser, 1, 1, 1);
        Assertions.assertEquals(Status.SUCCESS.getCode(), result.getCode().intValue());

        result = loggerService.queryLog(loginUser, 1, 0, 1);
        Assertions.assertEquals(Status.SUCCESS.getCode(), result.getCode().intValue());

        taskInstance.setLogPath("");
        assertThrowsServiceException(Status.QUERY_TASK_INSTANCE_LOG_ERROR,
                () -> loggerService.queryLog(loginUser, 1, 1, 1));
    }

    @Test
    public void testGetLogBytes() {

        User loginUser = new User();
        loginUser.setId(1);
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(1);
        taskInstance.setExecutorId(loginUser.getId() + 1);
        when(taskInstanceDao.queryById(1)).thenReturn(taskInstance);

        // task instance is null
        try {
            loggerService.getLogBytes(loginUser, 2);
        } catch (ServiceException e) {
            Assertions.assertEquals(new ServiceException("task instance is null or host is null").getMessage(),
                    e.getMessage());
            logger.error("testGetLogBytes error: {}", "task instance is null");
        }

        // task instance host is null
        try {
            loggerService.getLogBytes(loginUser, 1);
        } catch (ServiceException e) {
            Assertions.assertEquals(new ServiceException("task instance is null or host is null").getMessage(),
                    e.getMessage());
            logger.error("testGetLogBytes error: {}", "task instance host is null");
        }

        // PROJECT_NOT_EXIST
        taskInstance.setHost("127.0.0.1:" + nettyServerPort);
        taskInstance.setLogPath("/temp/log");
        doThrow(new ServiceException(Status.PROJECT_NOT_EXIST)).when(projectService)
                .checkProjectAndAuthThrowException(loginUser, taskInstance.getProjectCode(), VIEW_LOG);
        AssertionsHelper.assertThrowsServiceException(Status.PROJECT_NOT_EXIST,
                () -> loggerService.queryLog(loginUser, 1, 1, 1));

        // USER_NO_OPERATION_PERM
        doThrow(new ServiceException(Status.USER_NO_OPERATION_PERM)).when(projectService)
                .checkProjectAndAuthThrowException(loginUser, taskInstance.getProjectCode(), VIEW_LOG);
        AssertionsHelper.assertThrowsServiceException(Status.USER_NO_OPERATION_PERM,
                () -> loggerService.queryLog(loginUser, 1, 1, 1));

        // SUCCESS
        when(logClientDelegate.getWholeLogBytes(any())).thenReturn(new byte[0]);
        doNothing().when(projectService).checkProjectAndAuthThrowException(loginUser, taskInstance.getProjectCode(),
                DOWNLOAD_LOG);
        when(logClientDelegate.getWholeLogBytes(any())).thenReturn(new byte[0]);
        byte[] logBytes = loggerService.getLogBytes(loginUser, 1);
        Assertions.assertEquals(42, logBytes.length - String.valueOf(nettyServerPort).length());
    }

    @Test
    public void testQueryLogInSpecifiedProject() {
        long projectCode = 1L;

        User loginUser = new User();
        loginUser.setId(-1);
        loginUser.setUserType(UserType.GENERAL_USER);
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setProjectCode(projectCode);
        when(taskInstanceDao.queryById(1)).thenReturn(taskInstance);
        when(taskInstanceDao.queryById(10)).thenReturn(null);

        assertThrowsServiceException(Status.TASK_INSTANCE_NOT_FOUND,
                () -> loggerService.queryLog(loginUser, projectCode, 10, 1, 1));

        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setProjectCode(projectCode);
        taskDefinition.setCode(1L);

        // SUCCESS
        taskInstance.setTaskCode(1L);
        taskInstance.setId(1);
        taskInstance.setHost("127.0.0.1:" + nettyServerPort);
        taskInstance.setLogPath("/temp/log");
        doNothing().when(projectService).checkProjectAndAuthThrowException(loginUser, projectCode, VIEW_LOG);
        when(taskInstanceDao.queryById(1)).thenReturn(taskInstance);
        when(taskDefinitionDao.queryByCode(taskInstance.getTaskCode())).thenReturn(taskDefinition);
        assertDoesNotThrow(() -> loggerService.queryLog(loginUser, projectCode, 1, 1, 1));

        taskDefinition.setProjectCode(10);
        assertThrowsServiceException(Status.TASK_INSTANCE_NOT_FOUND,
                () -> loggerService.queryLog(loginUser, projectCode, 1, 1, 1));

        taskDefinition.setProjectCode(1);
        taskInstance.setProjectCode(2L);
        assertThrowsServiceException(Status.TASK_INSTANCE_NOT_FOUND,
                () -> loggerService.queryLog(loginUser, projectCode, 1, 1, 1));
        taskInstance.setProjectCode(projectCode);
        taskInstance.setId(10);
        when(taskInstanceDao.queryById(10)).thenReturn(taskInstance);

        when(logClientDelegate.getPartLogString(any(), anyInt(), anyInt())).thenReturn("log content");

        String result = loggerService.queryLog(loginUser, projectCode, 10, 1, 1);
        assertEquals("log content", result);

        taskInstance.setId(100);
        when(taskInstanceDao.queryById(100)).thenReturn(taskInstance);
        doThrow(new ServiceException("query log error")).when(logClientDelegate).getPartLogString(any(), anyInt(),
                anyInt());
        assertThrowsServiceException(Status.QUERY_TASK_INSTANCE_LOG_ERROR,
                () -> loggerService.queryLog(loginUser, projectCode, 10, 1, 1));
    }

    @Test
    public void testGetLogBytesInSpecifiedProject() {
        long projectCode = 1L;
        when(projectDao.queryByCode(projectCode)).thenReturn(getProject(projectCode));

        User loginUser = new User();
        loginUser.setId(-1);
        loginUser.setUserType(UserType.GENERAL_USER);
        Map<String, Object> result = new HashMap<>();
        putMsg(result, Status.SUCCESS, projectCode);
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setProjectCode(projectCode);
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setProjectCode(projectCode);
        taskDefinition.setCode(1L);
        // SUCCESS
        taskInstance.setTaskCode(1L);
        taskInstance.setId(1);
        taskInstance.setHost("127.0.0.1:" + nettyServerPort);
        taskInstance.setLogPath("/temp/log");
        doNothing().when(projectService).checkProjectAndAuthThrowException(loginUser, projectCode, DOWNLOAD_LOG);

        when(taskInstanceDao.queryById(1)).thenReturn(null);
        assertThrowsServiceException(
                Status.INTERNAL_SERVER_ERROR_ARGS, () -> loggerService.getLogBytes(loginUser, projectCode, 1));

        when(taskInstanceDao.queryById(1)).thenReturn(taskInstance);
        when(taskDefinitionDao.queryByCode(taskInstance.getTaskCode())).thenReturn(taskDefinition);
        when(logClientDelegate.getWholeLogBytes(any())).thenReturn(new byte[0]);
        assertDoesNotThrow(() -> loggerService.getLogBytes(loginUser, projectCode, 1));

        taskDefinition.setProjectCode(2L);
        assertThrowsServiceException(Status.INTERNAL_SERVER_ERROR_ARGS,
                () -> loggerService.getLogBytes(loginUser, projectCode, 1));

        taskDefinition.setProjectCode(1L);
        taskInstance.setProjectCode(2L);
        assertThrowsServiceException(Status.INTERNAL_SERVER_ERROR_ARGS,
                () -> loggerService.getLogBytes(loginUser, projectCode, 1));
        taskInstance.setProjectCode(projectCode);
        taskInstance.setId(100);
        when(taskInstanceDao.queryById(100)).thenReturn(taskInstance);
        doThrow(new ServiceException("download error")).when(logClientDelegate).getWholeLogBytes(any());
        assertThrowsServiceException(Status.DOWNLOAD_TASK_INSTANCE_LOG_FILE_ERROR,
                () -> loggerService.getLogBytes(loginUser, projectCode, 100));
    }

    @Test
    public void testLogPageCountsOnlyFileLines() {
        User user = new User();
        TaskInstance instance = new TaskInstance();
        instance.setId(42);
        instance.setProjectCode(100L);
        instance.setHost("127.0.0.1:18080");
        instance.setLogPath("/tmp/task.log");
        when(taskInstanceDao.queryById(42)).thenReturn(instance);
        when(logClientDelegate.getPartLogString(instance, 0, 200)).thenReturn("first\r\nsecond\r\n");
        when(logClientDelegate.getPartLogString(instance, 2, 200)).thenReturn("third\r\n");
        when(logClientDelegate.getPartLogString(instance, 3, 200)).thenReturn("");

        Result<ResponseTaskLog> first = loggerService.queryLog(user, 42, 0, 200);
        assertEquals(2, first.getData().getLineNum());
        Assertions.assertTrue(first.getData().getMessage().startsWith("[LOG-PATH]: "));
        Result<ResponseTaskLog> next = loggerService.queryLog(user, 42, 2, 200);
        assertEquals(1, next.getData().getLineNum());
        assertEquals("third\r\n", next.getData().getMessage());
        Result<ResponseTaskLog> empty = loggerService.queryLog(user, 42, 3, 200);
        assertEquals(0, empty.getData().getLineNum());
        assertEquals("", empty.getData().getMessage());
    }

    @Test
    public void testQueryLogChecksPermissionBeforeReportingMissingHost() {
        User user = new User();
        TaskInstance instance = new TaskInstance();
        instance.setProjectCode(100L);
        when(taskInstanceDao.queryById(42)).thenReturn(instance);
        doThrow(new ServiceException(Status.USER_NO_OPERATION_PERM)).when(projectService)
                .checkProjectAndAuthThrowException(user, 100L, VIEW_LOG);

        assertThrowsServiceException(Status.USER_NO_OPERATION_PERM,
                () -> loggerService.queryLog(user, 42, 0, 200));
        verifyNoInteractions(logClientDelegate);
    }

    /**
     * get mock Project
     *
     * @param projectCode projectCode
     * @return Project
     */
    private Project getProject(long projectCode) {
        Project project = new Project();
        project.setCode(projectCode);
        project.setId(1);
        project.setName("test");
        project.setUserId(1);
        return project;
    }

    private void putMsg(Map<String, Object> result, Status status, Object... statusParams) {
        result.put(Constants.STATUS, status);
        if (statusParams != null && statusParams.length > 0) {
            result.put(Constants.MSG, MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.put(Constants.MSG, status.getMsg());
        }
    }
}
