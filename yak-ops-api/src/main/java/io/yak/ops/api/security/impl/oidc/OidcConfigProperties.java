package io.yak.ops.api.security.impl.oidc;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * OIDC configuration properties
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.authentication.oidc")
public class OidcConfigProperties {

    private boolean enable = false;
    private Map<String, OidcProviderConfig> providers;

    @NestedConfigurationProperty
    private OidcUserConfig user = new OidcUserConfig();
}
