package io.yak.ops.api.security;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import io.yak.ops.api.configuration.ApiConfig;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

/**
 * Edge case tests for {@link SecurityConfig} that are not covered by Spring Boot integration tests.
 */
public class SecurityConfigEdgeCasesTest {

    private SecurityConfig newSecurityConfigWithType(String typeValue, Optional<?> oidcConfigOpt,
                                                     Optional<?> usersServiceOpt) throws Exception {
        @SuppressWarnings("unchecked")
        Optional orgOidc = (Optional) oidcConfigOpt;
        @SuppressWarnings("unchecked")
        Optional orgUsers = (Optional) usersServiceOpt;
        AutowireCapableBeanFactory beanFactory = mock(AutowireCapableBeanFactory.class);
        doNothing().when(beanFactory).autowireBean(Mockito.any());
        ApiConfig apiConfig = mock(ApiConfig.class);
        SecurityConfig securityConfig = new SecurityConfig(beanFactory, orgOidc, orgUsers, apiConfig);
        Field typeField = SecurityConfig.class.getDeclaredField("type");
        typeField.setAccessible(true);
        typeField.set(securityConfig, typeValue);
        return securityConfig;
    }

    @Test
    public void testBlankTypeDefaultsToPassword() throws Exception {
        SecurityConfig securityConfig = newSecurityConfigWithType("  ", Optional.empty(), Optional.empty());
        Authenticator authenticator = securityConfig.authenticator();
        Assertions.assertNotNull(authenticator, "Authenticator should not be null for blank type");
        // getType should still return the original injected value (blank) per current implementation
        Assertions.assertEquals("  ", securityConfig.getType());
    }

    @Test
    public void testInvalidTypeThrowsException() throws Exception {
        SecurityConfig securityConfig =
                newSecurityConfigWithType("NOT_A_VALID_TYPE", Optional.empty(), Optional.empty());
        Assertions.assertThrows(IllegalArgumentException.class, securityConfig::authenticator);
    }

    @Test
    public void testOidcTypeMissingBeansThrowsIllegalState() throws Exception {
        SecurityConfig securityConfig = newSecurityConfigWithType("OIDC", Optional.empty(), Optional.empty());
        Assertions.assertThrows(IllegalStateException.class, securityConfig::authenticator);
    }
}
