package io.yak.ops.api.security.impl.oidc;

import io.yak.ops.api.controller.AbstractControllerTest;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "security.authentication.type=OIDC",
        "security.authentication.oidc.enable=true",
        "security.authentication.oidc.providers.keycloak.display-name=Login with Keycloak",
        "security.authentication.oidc.providers.keycloak.issuer-uri=http://keycloak:8080/realms/yak-ops",
        "security.authentication.oidc.providers.keycloak.client-id=yak-ops-client",
        "security.authentication.oidc.providers.keycloak.client-secret=test-secret",
        "security.authentication.oidc.providers.keycloak.icon-uri=/icons/keycloak.png",
        "security.authentication.oidc.providers.keycloak.user-name-attribute=preferred_username",
        "security.authentication.oidc.providers.keycloak.groups-claim=groups",
        "security.authentication.oidc.user.auto-create=true",
        "security.authentication.oidc.user.default-tenant-code=default",
        "security.authentication.oidc.user.admin-group-mapping[0]=yak-ops-admins"
})
public class OidcConfigPropertiesTest extends AbstractControllerTest {

    @Autowired
    private OidcConfigProperties oidcConfigProperties;

    @Test
    public void testOidcConfigPropertiesLoading() {
        Assertions.assertTrue(oidcConfigProperties.isEnable());

        OidcProviderConfig keycloakConfig = oidcConfigProperties.getProviders().get("keycloak");
        Assertions.assertNotNull(keycloakConfig);
        Assertions.assertEquals("Login with Keycloak", keycloakConfig.getDisplayName());
        Assertions.assertEquals("http://keycloak:8080/realms/yak-ops", keycloakConfig.getIssuerUri());
        Assertions.assertEquals("yak-ops-client", keycloakConfig.getClientId());
        Assertions.assertEquals("test-secret", keycloakConfig.getClientSecret());
        Assertions.assertEquals("/icons/keycloak.png", keycloakConfig.getIconUri());
        Assertions.assertEquals("preferred_username", keycloakConfig.getUserNameAttribute());
        Assertions.assertEquals("groups", keycloakConfig.getGroupsClaim());

        OidcUserConfig userConfig = oidcConfigProperties.getUser();
        Assertions.assertNotNull(userConfig);
        Assertions.assertTrue(userConfig.isAutoCreate());
        Assertions.assertEquals("default", userConfig.getDefaultTenantCode());
        Assertions.assertEquals(Collections.singletonList("yak-ops-admins"),
                userConfig.getAdminGroupMapping());
    }
}
