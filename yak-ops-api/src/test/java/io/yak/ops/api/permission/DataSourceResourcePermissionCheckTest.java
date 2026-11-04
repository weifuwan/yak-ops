package io.yak.ops.api.permission;

import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.DataSourceDao;

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
public class DataSourceResourcePermissionCheckTest {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceResourcePermissionCheckTest.class);
    @InjectMocks
    private ResourcePermissionCheckServiceImpl.DataSourceResourcePermissionCheck dataSourceResourcePermissionCheck;

    @Mock
    private DataSourceDao dataSourceDao;

    @Test
    public void testPermissionCheck() {
        User user = getLoginUser();
        Assertions.assertTrue(dataSourceResourcePermissionCheck.permissionCheck(user.getId(), null, logger));
    }

    @Test
    public void testAuthorizationTypes() {
        List<AuthorizationType> authorizationTypes = dataSourceResourcePermissionCheck.authorizationTypes();
        Assertions.assertEquals(Collections.singletonList(AuthorizationType.DATASOURCE), authorizationTypes);
    }

    @Test
    public void testListAuthorizedResourceIds() {
        User user = getLoginUser();
        DataSource dataSource = new DataSource();
        Set<Integer> ids = new HashSet();
        ids.add(dataSource.getId());
        List<DataSource> dataSources = Arrays.asList(dataSource);

        Mockito.when(dataSourceDao.listAuthorizedDataSource(user.getId(), null)).thenReturn(dataSources);

        Assertions.assertEquals(ids, dataSourceResourcePermissionCheck.listAuthorizedResourceIds(user.getId(), logger));
    }

    private User getLoginUser() {
        User loginUser = new User();
        loginUser.setUserType(UserType.GENERAL_USER);
        loginUser.setUserName("test");
        loginUser.setId(1);
        return loginUser;
    }
}
