package io.yak.ops.api.security;

import io.yak.ops.api.controller.AbstractControllerTest;
import io.yak.ops.api.security.impl.sso.CasdoorAuthenticator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "security.authentication.type=CASDOOR_SSO",
        "casdoor.endpoint=http://localhost:8000",
        "casdoor.client-id=client-id",
        "casdoor.client-secret=client-secret",
        "casdoor.certificate=public-key",
        "casdoor.organization-name=built-in",
        "casdoor.application-name=app-built-in",
        "casdoor.redirect-url=http://localhost:8888/view/login/index.html",
        "security.authentication.casdoor.user.admin=admin"
})
public class SecurityConfigCasdoorTest extends AbstractControllerTest {

    @Autowired
    private SecurityConfig securityConfig;

    @Test
    public void testCasdoorAuthenticatorBean() {
        Authenticator authenticator = securityConfig.authenticator();
        Assertions.assertNotNull(authenticator);
        Assertions.assertInstanceOf(CasdoorAuthenticator.class, authenticator);
        Assertions.assertEquals("CASDOOR_SSO", securityConfig.getType());
    }
}
