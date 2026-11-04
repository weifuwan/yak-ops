package io.yak.ops.api.security;

import io.yak.ops.api.controller.AbstractControllerTest;
import io.yak.ops.api.security.impl.ldap.LdapService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "security.authentication.type=LDAP",
        "security.authentication.ldap.user.not-exist-action=CREATE"
})
public class SecurityConfigLDAPTest extends AbstractControllerTest {

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private LdapService ldapService;

    @Test
    public void testAuthenticator() {
        Authenticator authenticator = securityConfig.authenticator();
        Assertions.assertNotNull(authenticator);
    }

    @Test
    public void testLdapUserNotExistAction() {
        LdapUserNotExistActionType authenticator = ldapService.getLdapUserNotExistAction();
        Assertions.assertEquals(LdapUserNotExistActionType.CREATE, authenticator);

        boolean isCreateAction = ldapService.createIfUserNotExists();
        Assertions.assertEquals(Boolean.TRUE, isCreateAction);
    }
}
