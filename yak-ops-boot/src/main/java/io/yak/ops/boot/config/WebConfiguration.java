package io.yak.ops.boot.config;

import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.service.LoginService;
import io.yak.ops.security.web.YakAuthenticationInterceptor;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Central MVC runtime wiring for Yak Ops. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "yak.security",
        name = {"enabled", "database-enabled", "web-enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class WebConfiguration implements WebMvcConfigurer {

    @Resource
    private LoginService loginService;

    @Resource
    private YakSecurityProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new YakAuthenticationInterceptor(loginService, properties))
                .addPathPatterns("/**");
    }
}
