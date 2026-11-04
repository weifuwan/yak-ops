package io.yak.ops.api.security.impl.ldap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.api.controller.AbstractControllerTest;
import io.yak.ops.api.dto.LdapLoginResult;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.security.LdapUserNotExistActionType;
import io.yak.ops.api.service.SessionService;
import io.yak.ops.api.service.UsersService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.Session;
import io.yak.ops.dao.entity.User;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "security.authentication.type=LDAP",
        "security.authentication.ldap.user.admin=read-only-admin",
        "security.authentication.ldap.urls=ldap://ldap.forumsys.com:389/",
        "security.authentication.ldap.base-dn=dc=example,dc=com",
        "security.authentication.ldap.username=cn=read-only-admin,dc=example,dc=com",
        "security.authentication.ldap.password=password",
        "security.authentication.ldap.user.identity-attribute=uid",
        "security.authentication.ldap.user.email-attribute=mail",
        "security.authentication.ldap.user.not-exist-action=CREATE",
        "security.authentication.ldap.ssl.enable=false",
        "security.authentication.ldap.ssl.trust-store=",
        "security.authentication.ldap.ssl.trust-store-password=",
})
public class LdapAuthenticatorTest extends AbstractControllerTest {

    private static Logger logger = LoggerFactory.getLogger(LdapAuthenticatorTest.class);
    @Autowired
    protected AutowireCapableBeanFactory beanFactory;
    @MockBean(name = "ldapService")
    private LdapService ldapService;
    @MockBean(name = "sessionServiceImpl")
    private SessionService sessionService;
    @Spy
    private UsersService usersService;

    private LdapAuthenticator ldapAuthenticator;

    // test param
    private User mockUser;
    private Session mockSession;

    private final String ldapUid = "test";
    private final String ldapUserPwd = "password";
    private final String ldapEmail = "test@example.com";
    private final String ip = "127.0.0.1";
    private final UserType userType = UserType.GENERAL_USER;
    private final LdapLoginResult ldapLoginResultSuccess = new LdapLoginResult(true, ldapEmail, userType, ldapUid);
    private final LdapLoginResult ldapLoginResultFailed = new LdapLoginResult(false, ldapEmail, userType, ldapUid);

    @Override
    @BeforeEach
    public void setUp() {
        ldapAuthenticator = new LdapAuthenticator();
        beanFactory.autowireBean(ldapAuthenticator);

        mockUser = new User();
        mockUser.setId(1);
        mockUser.setUserName(ldapUid);
        mockUser.setEmail(ldapEmail);
        mockUser.setUserType(userType);
        mockUser.setState(Flag.YES.getCode());

        mockSession = new Session();
        mockSession.setId(UUID.randomUUID().toString());
        mockSession.setIp(ip);
        mockSession.setUserId(1);
        mockSession.setLastLoginTime(new Date());
    }

    @Test
    public void testAuthenticate() {
        when(ldapService.ldapLogin(ldapUid, ldapUserPwd)).thenReturn(ldapLoginResultSuccess);
        when(sessionService.createSessionIfAbsent(Mockito.any(User.class))).thenReturn(mockSession);

        // test username pwd correct and user not exist, config user not exist action deny, so login denied
        when(ldapService.getLdapUserNotExistAction()).thenReturn(LdapUserNotExistActionType.DENY);
        when(ldapService.createIfUserNotExists()).thenReturn(false);
        Result<Map<String, String>> result = ldapAuthenticator.authenticate(ldapUid, ldapUserPwd, ip);
        Assertions.assertEquals(Status.USER_NAME_PASSWD_ERROR.getCode(), (int) result.getCode());

        // test username pwd correct and user not exist, config user not exist action create, so login success
        when(ldapService.getLdapUserNotExistAction()).thenReturn(LdapUserNotExistActionType.CREATE);
        when(ldapService.createIfUserNotExists()).thenReturn(true);
        result = ldapAuthenticator.authenticate(ldapUid, ldapUserPwd, ip);
        Assertions.assertEquals(Status.SUCCESS.getCode(), (int) result.getCode());
        logger.info(result.toString());

        // test username pwd correct and user not exist, config action create but can't create session, so login failed
        when(sessionService.createSessionIfAbsent(Mockito.any(User.class))).thenReturn(null);
        result = ldapAuthenticator.authenticate(ldapUid, ldapUserPwd, ip);
        Assertions.assertEquals(Status.LOGIN_SESSION_FAILED.getCode(), (int) result.getCode());

        // test username pwd error, login failed
        when(sessionService.createSessionIfAbsent(Mockito.any(User.class))).thenReturn(mockSession);
        when(ldapService.ldapLogin(ldapUid, "123")).thenReturn(ldapLoginResultFailed);
        result = ldapAuthenticator.authenticate(ldapUid, "123", ip);
        Assertions.assertEquals(Status.USER_NAME_PASSWD_ERROR.getCode(), (int) result.getCode());
    }

    @Test
    public void testGetAuthUser() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(usersService.queryUser(mockUser.getId())).thenReturn(mockUser);
        when(sessionService.getSession(any())).thenReturn(mockSession);

        User user = ldapAuthenticator.getAuthUser(request);
        Assertions.assertNotNull(user);

        when(sessionService.getSession(any())).thenReturn(null);
        user = ldapAuthenticator.getAuthUser(request);
        Assertions.assertNull(user);
    }
}
