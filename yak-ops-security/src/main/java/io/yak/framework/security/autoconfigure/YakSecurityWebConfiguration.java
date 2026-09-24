package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.web.YakAuthenticationInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Yak Security Web runtime.
 *
 * <p>Only authentication interception is owned here. HTTP controllers are owned by yak-ops-boot.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "yak.security",
        name = {
                "database-enabled",
                "web-enabled"
        },
        havingValue = "true",
        matchIfMissing = true)
public class YakSecurityWebConfiguration
        implements WebMvcConfigurer {

  private final YakAuthenticationInterceptor
          authenticationInterceptor;

  public YakSecurityWebConfiguration(
          LoginService loginService,
          YakSecurityProperties properties) {
    this.authenticationInterceptor =
            new YakAuthenticationInterceptor(
                    loginService,
                    properties);
  }

  @Override
  public void addInterceptors(
          InterceptorRegistry registry) {
    registry.addInterceptor(
                    authenticationInterceptor)
            .addPathPatterns("/**");
  }
}
