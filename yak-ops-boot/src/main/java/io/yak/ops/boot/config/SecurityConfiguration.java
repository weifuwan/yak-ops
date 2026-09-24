package io.yak.ops.boot.config;

import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.security.authentication.HttpSessionAuthenticationManager;
import io.yak.ops.security.bootstrap.YakSecurityBootstrapInitializer;
import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.extend.CurrentUserProvider;
import io.yak.ops.security.extend.LoginExtend;
import io.yak.ops.security.extend.PasswordEncoder;
import io.yak.ops.security.extend.impl.DefaultCurrentUserProvider;
import io.yak.ops.security.extend.impl.DefaultLoginExtendImpl;
import io.yak.ops.security.extend.impl.DefaultPasswordEncoder;
import io.yak.ops.security.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Central Security runtime assembly for Yak Ops. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YakSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    @ConditionalOnProperty(
            prefix = "yak.security",
            name = {"enabled", "database-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    PasswordEncoder passwordEncoder() {
        return new DefaultPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationManager.class)
    @ConditionalOnProperty(
            prefix = "yak.security",
            name = {"enabled", "database-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    AuthenticationManager authenticationManager(YakSecurityProperties properties) {
        return new HttpSessionAuthenticationManager(
                properties.getAuthentication().getIdleTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(LoginExtend.class)
    @ConditionalOnProperty(
            prefix = "yak.security",
            name = {"enabled", "database-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    LoginExtend loginExtend(
            UserService userService,
            PasswordEncoder passwordEncoder,
            YakSecurityProperties properties,
            AuthenticationManager authenticationManager) {
        return new DefaultLoginExtendImpl(userService, passwordEncoder, properties, authenticationManager);
    }

    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    @ConditionalOnProperty(
            prefix = "yak.security",
            name = {"enabled", "database-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    CurrentUserProvider currentUserProvider(AuthenticationManager authenticationManager) {
        return new DefaultCurrentUserProvider(authenticationManager);
    }

    @Bean
    @ConditionalOnBean(UserService.class)
    @ConditionalOnProperty(prefix = "yak.security.bootstrap", name = "enabled", havingValue = "true")
    YakSecurityBootstrapInitializer yakSecurityBootstrapInitializer(
            YakSecurityProperties properties, UserService userService) {
        return new YakSecurityBootstrapInitializer(properties, userService);
    }
}
