package io.yak.ops.api.security.impl.pwd;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.api.controller.AbstractControllerTest;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.service.SessionService;
import io.yak.ops.api.service.UsersService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.Session;
import io.yak.ops.dao.entity.User;

import java.util.Date;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.mock.mockito.MockBean;

public class PasswordAuthenticatorTest extends AbstractControllerTest {

    private static Logger logger = LoggerFactory.getLogger(PasswordAuthenticatorTest.class);

    @Autowired
    private AutowireCapableBeanFactory beanFactory;
    @MockBean
    private SessionService sessionService;
    @MockBean
    private UsersService usersService;

    private PasswordAuthenticator authenticator;

    private User mockUser;
    private Session mockSession;

    @Override
    @BeforeEach
    public void setUp() {
        authenticator = new PasswordAuthenticator();
        beanFactory.autowireBean(authenticator);

        mockUser = new User();
        mockUser.setUserName("test");
        mockUser.setEmail("test@test.com");
        mockUser.setUserPassword("test");
        mockUser.setId(1);
        mockUser.setState(1);

        mockSession = new Session();
        mockSession.setId(UUID.randomUUID().toString());
        mockSession.setIp("127.0.0.1");
        mockSession.setUserId(1);
        mockSession.setLastLoginTime(new Date());
    }

    @Test
    public void testLogin() {
        when(usersService.queryUser("test", "test")).thenReturn(mockUser);
        User login = authenticator.login("test", "test");
        Assertions.assertNotNull(login);
    }

    @Test
    public void testAuthenticate() {
        when(usersService.queryUser("test", "test")).thenReturn(mockUser);
        when(sessionService.createSessionIfAbsent(mockUser)).thenReturn(mockSession);
        Result result = authenticator.authenticate("test", "test", "127.0.0.1");
        Assertions.assertEquals(Status.SUCCESS.getCode(), (int) result.getCode());
        logger.info(result.toString());

        mockUser.setState(0);
        when(usersService.queryUser("test", "test")).thenReturn(mockUser);
        when(sessionService.createSessionIfAbsent(mockUser)).thenReturn(mockSession);
        Result result1 = authenticator.authenticate("test", "test", "127.0.0.1");
        Assertions.assertEquals(Status.USER_DISABLED.getCode(), (int) result1.getCode());
        logger.info(result1.toString());
    }

    @Test
    public void testGetAuthUser() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(usersService.queryUser(mockUser.getId())).thenReturn(mockUser);
        when(sessionService.getSession(any())).thenReturn(mockSession);

        User user = authenticator.getAuthUser(request);
        Assertions.assertNotNull(user);
    }
}
