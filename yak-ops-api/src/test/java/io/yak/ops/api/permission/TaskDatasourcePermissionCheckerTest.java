package io.yak.ops.api.permission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.entity.User;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class TaskDatasourcePermissionCheckerTest {

    private static final int USER_ID = 1;
    private static final int DATASOURCE_ID = 42;

    @Mock
    private ResourcePermissionCheckService resourcePermissionCheckService;

    @Mock
    private DataSourceDao dataSourceDao;

    private TaskDatasourcePermissionChecker taskDatasourcePermissionChecker;

    private User loginUser;

    @BeforeEach
    public void setUp() {
        taskDatasourcePermissionChecker = new TaskDatasourcePermissionChecker(resourcePermissionCheckService, dataSourceDao);
        loginUser = new User();
        loginUser.setId(USER_ID);
        loginUser.setUserType(UserType.GENERAL_USER);
    }

    @Test
    public void shouldRejectUnauthorizedDatasourceReferencedByTask() {
        Mockito.when(resourcePermissionCheckService.resourcePermissionCheck(
                eq(AuthorizationType.DATASOURCE), any(Object[].class), eq(USER_ID), any(Logger.class)))
                .thenReturn(false);

        ServiceException exception = Assertions.assertThrows(ServiceException.class,
                () -> taskDatasourcePermissionChecker.checkPermission(
                        loginUser, Collections.singletonList(getRemoteShellTaskDefinition())));

        Assertions.assertEquals(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION.getCode(), exception.getCode());
        ArgumentCaptor<Object[]> datasourceIdsCaptor = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(resourcePermissionCheckService).resourcePermissionCheck(
                eq(AuthorizationType.DATASOURCE), datasourceIdsCaptor.capture(), eq(USER_ID), any(Logger.class));
        Assertions.assertArrayEquals(new Integer[]{DATASOURCE_ID}, datasourceIdsCaptor.getValue());
    }

    @Test
    public void shouldAllowAuthorizedDatasourceReferencedByTask() {
        Mockito.when(resourcePermissionCheckService.resourcePermissionCheck(
                eq(AuthorizationType.DATASOURCE), any(Object[].class), eq(USER_ID), any(Logger.class)))
                .thenReturn(true);

        Assertions.assertDoesNotThrow(() -> taskDatasourcePermissionChecker.checkPermission(
                loginUser, Collections.singletonList(getRemoteShellTaskDefinition())));
    }

    @Test
    public void shouldRejectDatasourceOwnedByAnotherProject() {
        TaskDefinition task = getRemoteShellTaskDefinition();
        task.setProjectCode(1001L);
        Assertions.assertThrows(ServiceException.class,
                () -> taskDatasourcePermissionChecker.checkPermission(loginUser, Collections.singletonList(task)));
        Mockito.verify(dataSourceDao).queryByIdAndProjectCode(DATASOURCE_ID, 1001L);
        Mockito.verifyNoInteractions(resourcePermissionCheckService);
    }

    @Test
    public void shouldAllowDatasourceInSameProject() {
        TaskDefinition task = getRemoteShellTaskDefinition();
        task.setProjectCode(1001L);
        DataSource source = new DataSource();
        source.setProjectCode(1001L);
        Mockito.when(dataSourceDao.queryByIdAndProjectCode(DATASOURCE_ID, 1001L)).thenReturn(source);
        Assertions.assertDoesNotThrow(
                () -> taskDatasourcePermissionChecker.checkPermission(loginUser, Collections.singletonList(task)));
        Mockito.verifyNoInteractions(resourcePermissionCheckService);
    }

    @Test
    public void shouldSkipPermissionCheckForTaskWithoutDatasource() {
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setTaskType("SHELL");
        taskDefinition.setTaskParams("{\"rawScript\":\"echo test\"}");

        taskDatasourcePermissionChecker.checkPermission(loginUser, Collections.singletonList(taskDefinition));

        Mockito.verifyNoInteractions(resourcePermissionCheckService);
    }

    private TaskDefinition getRemoteShellTaskDefinition() {
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setTaskType("REMOTESHELL");
        taskDefinition.setTaskParams(
                "{\"rawScript\":\"echo test\",\"type\":\"SSH\",\"datasource\":" + DATASOURCE_ID + "}");
        return taskDefinition;
    }
}
