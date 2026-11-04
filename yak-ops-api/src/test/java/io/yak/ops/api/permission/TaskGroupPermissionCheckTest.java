package io.yak.ops.api.permission;

import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.TaskGroup;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.mapper.TaskGroupMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
public class TaskGroupPermissionCheckTest {

    private static final Logger logger = LoggerFactory.getLogger(TaskGroupPermissionCheckTest.class);
    @InjectMocks
    private ResourcePermissionCheckServiceImpl.TaskGroupPermissionCheck taskGroupPermissionCheck;

    @Mock
    private TaskGroupMapper taskGroupMapper;

    @Test
    public void testPermissionCheck() {
        User user = getLoginUser();
        Assertions.assertTrue(taskGroupPermissionCheck.permissionCheck(user.getId(), null, logger));
    }

    @Test
    public void testAuthorizationTypes() {
        List<AuthorizationType> authorizationTypes = taskGroupPermissionCheck.authorizationTypes();
        Assertions.assertEquals(Collections.singletonList(AuthorizationType.TASK_GROUP), authorizationTypes);
    }

    @Test
    public void testListAuthorizedResourceIds() {
        User user = getLoginUser();
        TaskGroup taskGroup = new TaskGroup();
        Set<Integer> ids = new HashSet<>();
        ids.add(taskGroup.getId());
        List<TaskGroup> taskGroups = Arrays.asList(taskGroup);

        Mockito.when(taskGroupMapper.listAuthorizedResource(user.getId())).thenReturn(taskGroups);

        Assertions.assertEquals(ids, taskGroupPermissionCheck.listAuthorizedResourceIds(user.getId(), logger));
    }

    private User getLoginUser() {
        User loginUser = new User();
        loginUser.setUserType(UserType.GENERAL_USER);
        loginUser.setUserName("test");
        loginUser.setId(1);
        return loginUser;
    }
}
