package io.yak.ops.boot.config.security;

import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.service.LoginService;
import io.yak.ops.security.web.YakAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class SecurityWebConfiguration implements WebMvcConfigurer {

  private final YakAuthenticationInterceptor authenticationInterceptor;

  public SecurityWebConfiguration(
      LoginService loginService,
      YakSecurityProperties properties) {
    this.authenticationInterceptor = new YakAuthenticationInterceptor(loginService, properties);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authenticationInterceptor).addPathPatterns("/**");
  }
}
