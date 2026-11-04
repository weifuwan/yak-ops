package io.yak.ops.api.configuration;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConditionalOnProperty(prefix = "security.authentication.oauth2", name = "enable", havingValue = "true")
@ConfigurationProperties(prefix = "security.authentication.oauth2")
public class OAuth2Configuration {

    private Map<String, OAuth2ClientProperties> provider = new HashMap<>();

    @Data
    public static class OAuth2ClientProperties {

        private String authorizationUri;
        private String clientId;
        private String redirectUri;
        private String clientSecret;
        private String tokenUri;
        private String userInfoUri;
        private String callbackUrl;
        private String iconUri;
        private String provider;

    }
}
