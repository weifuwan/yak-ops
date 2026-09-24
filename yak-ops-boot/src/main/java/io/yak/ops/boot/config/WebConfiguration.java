package io.yak.ops.boot.config;

import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.web.YakAuthenticationInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Central MVC runtime wiring for Yak Ops. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "yak.security",
    name = {"database-enabled", "web-enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class WebConfiguration implements WebMvcConfigurer {

  private final YakAuthenticationInterceptor authenticationInterceptor;

  public WebConfiguration(LoginService loginService, YakSecurityProperties properties) {
    this.authenticationInterceptor = new YakAuthenticationInterceptor(loginService, properties);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authenticationInterceptor).addPathPatterns("/**");
  }
}
