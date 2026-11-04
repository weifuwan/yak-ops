package io.yak.ops.api.security;

import io.yak.ops.api.controller.AbstractControllerTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "security.authentication.type=PASSWORD",
})
public class SecurityConfigPasswordTest extends AbstractControllerTest {

    @Autowired
    private SecurityConfig securityConfig;

    @Test
    public void testAuthenticator() {
        Authenticator authenticator = securityConfig.authenticator();
        Assertions.assertNotNull(authenticator);
    }
}
