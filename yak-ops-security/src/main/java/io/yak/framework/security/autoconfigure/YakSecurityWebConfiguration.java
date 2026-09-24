package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.controller.v1.CommonController;
import io.yak.framework.security.controller.v1.ConfigController;
import io.yak.framework.security.controller.v1.DeptController;
import io.yak.framework.security.controller.v1.LoginController;
import io.yak.framework.security.controller.v1.MessageController;
import io.yak.framework.security.controller.v1.PermissionController;
import io.yak.framework.security.controller.v1.ProjectController;
import io.yak.framework.security.controller.v1.ResourceController;
import io.yak.framework.security.controller.v1.RoleController;
import io.yak.framework.security.controller.v1.UserController;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.service.RbacPermissionService;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.YakAuthenticationInterceptor;
import io.yak.framework.security.web.YakSecurityExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "yak.security",
        name = {
                "database-enabled",
                "web-enabled"
        },
        havingValue = "true",
        matchIfMissing = true
)
@Import({
        CommonController.class,
        ConfigController.class,
        DeptController.class,
        LoginController.class,
        MessageController.class,
        PermissionController.class,
        ProjectController.class,
        ResourceController.class,
        RoleController.class,
        UserController.class,
        YakSecurityExceptionHandler.class
})
public class YakSecurityWebConfiguration implements WebMvcConfigurer {

  private final YakAuthenticationInterceptor authenticationInterceptor;

  public YakSecurityWebConfiguration(
          LoginService loginService,
          YakSecurityProperties properties,
          RbacPermissionService permissionService,
          CurrentUserProvider currentUserProvider) {
    this.authenticationInterceptor =
            new YakAuthenticationInterceptor(
                    loginService, properties,
                    permissionService, currentUserProvider);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authenticationInterceptor)
            .addPathPatterns("/**");
  }
}
