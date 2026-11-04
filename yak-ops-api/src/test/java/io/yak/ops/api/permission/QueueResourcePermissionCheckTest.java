package io.yak.ops.api.permission;

import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.Queue;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.QueueDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
public class QueueResourcePermissionCheckTest {

    private static final Logger logger = LoggerFactory.getLogger(QueueResourcePermissionCheckTest.class);
    @InjectMocks
    private ResourcePermissionCheckServiceImpl.QueueResourcePermissionCheck queueResourcePermissionCheck;

    @Mock
    private QueueDao queueDao;

    @Test
    public void testPermissionCheck() {
        User user = getLoginUser();
        Assertions.assertFalse(queueResourcePermissionCheck.permissionCheck(user.getId(), null, logger));
    }

    @Test
    public void testAuthorizationTypes() {
        List<AuthorizationType> authorizationTypes = queueResourcePermissionCheck.authorizationTypes();
        Assertions.assertEquals(Collections.singletonList(AuthorizationType.QUEUE), authorizationTypes);
    }

    @Test
    public void testListAuthorizedResourceIds() {
        Queue queue = new Queue();
        Mockito.when(queueDao.queryAll()).thenReturn(Arrays.asList(queue));
        // GENERAL_USER
        User user = getLoginUser();
        Assertions.assertEquals(0, queueResourcePermissionCheck.listAuthorizedResourceIds(user.getId(), logger).size());

        // ADMIN_USER
        user = getAdminUser();
        Assertions.assertEquals(1, queueResourcePermissionCheck.listAuthorizedResourceIds(user.getId(), logger).size());
    }

    private User getLoginUser() {
        User loginUser = new User();
        loginUser.setUserType(UserType.GENERAL_USER);
        loginUser.setUserName("test");
        loginUser.setId(1);
        return loginUser;
    }

    private User getAdminUser() {
        User loginUser = new User();
        loginUser.setUserType(UserType.ADMIN_USER);
        loginUser.setUserName("test");
        loginUser.setId(0);
        return loginUser;
    }
}
