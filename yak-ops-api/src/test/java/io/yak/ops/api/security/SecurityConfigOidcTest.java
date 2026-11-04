package io.yak.ops.api.security;

import io.yak.ops.api.controller.AbstractControllerTest;
import io.yak.ops.api.security.impl.oidc.OidcAuthenticator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "security.authentication.type=OIDC"
})
public class SecurityConfigOidcTest extends AbstractControllerTest {

    @Autowired
    private SecurityConfig securityConfig;

    @Test
    public void testOidcAuthenticatorBean() {
        Authenticator authenticator = securityConfig.authenticator();

        Assertions.assertNotNull(authenticator);
        Assertions.assertInstanceOf(OidcAuthenticator.class, authenticator);
        Assertions.assertEquals("OIDC", securityConfig.getType());
    }
}
