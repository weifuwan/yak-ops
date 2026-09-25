package io.yak.ops.boot.config;

import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.security.authentication.HttpSessionAuthenticationManager;
import io.yak.ops.security.bootstrap.YakSecurityBootstrapInitializer;
import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.constant.SecurityConstants;
import io.yak.ops.security.extend.PasswordEncoder;
import io.yak.ops.security.extend.impl.DefaultPasswordEncoder;
import io.yak.ops.security.service.LoginService;
import io.yak.ops.security.service.UserService;
import io.yak.ops.security.web.YakAuthenticationInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 统一装配 Yak Ops 的 Security 与认证 Web Runtime。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YakSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    @ConditionalOnProperty(
            prefix = SecurityConstants.CONFIG_PREFIX,
            name = {"enabled", "database-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    PasswordEncoder passwordEncoder() {
        return new DefaultPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationManager.class)
    @ConditionalOnProperty(
            prefix = SecurityConstants.CONFIG_PREFIX,
            name = {"enabled", "database-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    AuthenticationManager authenticationManager(YakSecurityProperties properties) {
        return new HttpSessionAuthenticationManager(
                properties.getAuthentication().getIdleTimeout());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = SecurityConstants.CONFIG_PREFIX,
            name = {"enabled", "database-enabled", "web-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    WebMvcConfigurer securityWebMvcConfigurer(LoginService loginService, YakSecurityProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new YakAuthenticationInterceptor(loginService, properties))
                        .addPathPatterns("/**");
            }
        };
    }

    @Bean
    @ConditionalOnBean(UserService.class)
    @ConditionalOnProperty(prefix = SecurityConstants.BOOTSTRAP_CONFIG_PREFIX, name = "enabled", havingValue = "true")
    YakSecurityBootstrapInitializer yakSecurityBootstrapInitializer(
            YakSecurityProperties properties, UserService userService) {
        return new YakSecurityBootstrapInitializer(properties, userService);
    }
}
