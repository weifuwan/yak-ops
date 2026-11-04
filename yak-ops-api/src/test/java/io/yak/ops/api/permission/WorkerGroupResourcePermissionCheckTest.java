package io.yak.ops.api.permission;

import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkerGroup;
import io.yak.ops.dao.repository.WorkerGroupDao;

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
public class WorkerGroupResourcePermissionCheckTest {

    private static final Logger logger = LoggerFactory.getLogger(WorkerGroupResourcePermissionCheckTest.class);
    @InjectMocks
    private ResourcePermissionCheckServiceImpl.WorkerGroupResourcePermissionCheck workerGroupResourcePermissionCheck;

    @Mock
    private WorkerGroupDao workerGroupDao;

    @Test
    public void testPermissionCheck() {
        User user = getLoginUser();
        Assertions.assertFalse(workerGroupResourcePermissionCheck.permissionCheck(user.getId(), null, logger));
    }

    @Test
    public void testAuthorizationTypes() {
        List<AuthorizationType> authorizationTypes = workerGroupResourcePermissionCheck.authorizationTypes();
        Assertions.assertEquals(Collections.singletonList(AuthorizationType.WORKER_GROUP), authorizationTypes);
    }

    @Test
    public void testListAuthorizedResourceIds() {
        User user = getLoginUser();
        WorkerGroup workerGroup = new WorkerGroup();
        Set<Integer> ids = new HashSet();
        ids.add(workerGroup.getId());
        List<WorkerGroup> workerGroups = Arrays.asList(workerGroup);

        Mockito.when(workerGroupDao.queryAllWorkerGroup()).thenReturn(workerGroups);

        Assertions.assertEquals(ids,
                workerGroupResourcePermissionCheck.listAuthorizedResourceIds(user.getId(), logger));
    }

    private User getLoginUser() {
        User loginUser = new User();
        loginUser.setUserType(UserType.GENERAL_USER);
        loginUser.setUserName("test");
        loginUser.setId(1);
        return loginUser;
    }
}
