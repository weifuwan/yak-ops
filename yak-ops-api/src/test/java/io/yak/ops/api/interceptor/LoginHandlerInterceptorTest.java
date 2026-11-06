package io.yak.ops.api.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.yak.ops.api.ApiApplicationServer;
import io.yak.ops.api.security.Authenticator;
import io.yak.ops.common.enums.ProfileType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.UserDao;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles(value = {ProfileType.H2})
@SpringBootTest(classes = ApiApplicationServer.class)
@Transactional
@Rollback
public class LoginHandlerInterceptorTest {

    private static final Logger logger = LoggerFactory.getLogger(LoginHandlerInterceptorTest.class);

    @Autowired
    LoginHandlerInterceptor interceptor;
    @MockBean(name = "authenticator")
    private Authenticator authenticator;
    @MockBean(name = "userDao")
    private UserDao userDao;

    @Test
    public void testPreHandle() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        // test no token and no cookie
        Assertions.assertFalse(interceptor.preHandle(request, response, null));

        User mockUser = new User();
        mockUser.setId(1);
        mockUser.setUserType(UserType.GENERAL_USER);
        mockUser.setState(1);

        // test no token
        when(authenticator.getAuthUser(request)).thenReturn(mockUser);
        Assertions.assertTrue(interceptor.preHandle(request, response, null));

        // test token
        String token = "123456";
        when(request.getHeader("token")).thenReturn(token);
        when(userDao.queryUserByToken(eq(token), any(Date.class))).thenReturn(mockUser);
        Assertions.assertTrue(interceptor.preHandle(request, response, null));

        // test disable user
        mockUser.setState(0);
        when(authenticator.getAuthUser(request)).thenReturn(mockUser);
        Assertions.assertFalse(interceptor.preHandle(request, response, null));
    }
}
