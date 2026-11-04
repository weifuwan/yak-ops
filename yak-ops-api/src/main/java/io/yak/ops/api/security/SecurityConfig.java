package io.yak.ops.api.security;

import io.yak.ops.api.configuration.ApiConfig;
import io.yak.ops.api.security.impl.ldap.LdapAuthenticator;
import io.yak.ops.api.security.impl.oidc.OidcAuthenticator;
import io.yak.ops.api.security.impl.oidc.OidcConfigProperties;
import io.yak.ops.api.security.impl.pwd.PasswordAuthenticator;
import io.yak.ops.api.security.impl.sso.CasdoorAuthenticator;
import io.yak.ops.api.service.UsersService;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class SecurityConfig {

    @Value("${security.authentication.type:PASSWORD}")
    private String type;

    private final AutowireCapableBeanFactory beanFactory;
    private final OidcConfigProperties oidcConfig;
    private final UsersService usersService;
    private AuthenticationType authenticationType;
    private final ApiConfig apiConfig;

    @Autowired
    public SecurityConfig(AutowireCapableBeanFactory beanFactory,
                          Optional<OidcConfigProperties> oidcConfig,
                          Optional<UsersService> usersService,
                          ApiConfig apiConfig) {
        this.beanFactory = beanFactory;
        this.oidcConfig = oidcConfig.orElse(null);
        this.usersService = usersService.orElse(null);
        this.apiConfig = apiConfig;
    }

    private void setAuthenticationType(String type) {
        if (StringUtils.isBlank(type)) {
            log.info("security.authentication.type configuration is empty, the default value 'PASSWORD'");
            this.authenticationType = AuthenticationType.PASSWORD;
            return;
        }

        this.authenticationType = AuthenticationType.valueOf(type);
    }

    @Bean(name = "authenticator")
    @Primary
    public Authenticator authenticator() {
        setAuthenticationType(type);
        Authenticator authenticator;
        switch (authenticationType) {
            case PASSWORD:
                authenticator = new PasswordAuthenticator();
                break;
            case LDAP:
                authenticator = new LdapAuthenticator();
                break;
            case CASDOOR_SSO:
                authenticator = new CasdoorAuthenticator();
                break;
            case OIDC:
                if (oidcConfig == null || usersService == null) {
                    throw new IllegalStateException(
                            "OIDC authentication is configured, but required beans are not available.");
                }
                authenticator = new OidcAuthenticator(oidcConfig, usersService, apiConfig);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + authenticationType);
        }
        beanFactory.autowireBean(authenticator);
        return authenticator;
    }

    public String getType() {
        return type;
    }
}
