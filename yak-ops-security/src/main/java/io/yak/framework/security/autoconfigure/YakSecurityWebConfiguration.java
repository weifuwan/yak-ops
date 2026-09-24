package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.controller.v1.LoginController;
import io.yak.framework.security.controller.v1.UserController;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.web.YakAuthenticationInterceptor;
import io.yak.framework.security.web.YakSecurityExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Yak Security Web 入口。
 *
 * <p>当前只发布登录与用户管理接口。</p>
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
@Import({
        LoginController.class,
        UserController.class,
        YakSecurityExceptionHandler.class
})
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
