package io.yak.ops.boot.config.security;

import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.security.authentication.HttpSessionAuthenticationManager;
import io.yak.ops.security.bootstrap.YakSecurityBootstrapInitializer;
import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.context.CurrentUser;
import io.yak.ops.security.context.DefaultCurrentUser;
import io.yak.ops.security.context.YakSecurityContextFilter;
import io.yak.ops.security.dao.PermissionDao;
import io.yak.ops.security.dao.UserRoleDao;
import io.yak.ops.security.extend.CurrentUserProvider;
import io.yak.ops.security.extend.LoginExtend;
import io.yak.ops.security.extend.OperationLogExtend;
import io.yak.ops.security.extend.PasswordEncoder;
import io.yak.ops.security.extend.PermissionExtend;
import io.yak.ops.security.extend.ResourceExtend;
import io.yak.ops.security.extend.impl.DefaultCurrentUserProvider;
import io.yak.ops.security.extend.impl.DefaultLoginExtendImpl;
import io.yak.ops.security.extend.impl.DefaultPasswordEncoder;
import io.yak.ops.security.extend.impl.DefaultPermissionExtend;
import io.yak.ops.security.extend.impl.DefaultResourceExtendImpl;
import io.yak.ops.security.extend.impl.NoOpOperationLogExtend;
import io.yak.ops.security.notification.DefaultNotificationPublisher;
import io.yak.ops.security.notification.NotificationPublisher;
import io.yak.ops.security.permission.PermissionRegistrationInitializer;
import io.yak.ops.security.permission.PermissionRegistrationService;
import io.yak.ops.security.service.MessageService;
import io.yak.ops.security.service.RolePermissionService;
import io.yak.ops.security.service.RoleService;
import io.yak.ops.security.service.UserService;
import io.yak.ops.security.service.impl.AuthorizationSnapshotService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YakSecurityProperties.class)
public class SecurityConfiguration {

  @Bean
  @ConditionalOnMissingBean(AuthenticationManager.class)
  AuthenticationManager authenticationManager(YakSecurityProperties properties) {
    return new HttpSessionAuthenticationManager(properties.getAuthentication().getIdleTimeout());
  }

  @Bean
  @ConditionalOnMissingBean(PasswordEncoder.class)
  PasswordEncoder passwordEncoder() {
    return new DefaultPasswordEncoder();
  }

  @Bean
  @ConditionalOnMissingBean(LoginExtend.class)
  LoginExtend loginExtend(
      UserService userService,
      PasswordEncoder passwordEncoder,
      YakSecurityProperties properties,
      AuthenticationManager authenticationManager) {
    return new DefaultLoginExtendImpl(
        userService, passwordEncoder, properties, authenticationManager);
  }

  @Bean
  @ConditionalOnMissingBean(CurrentUserProvider.class)
  CurrentUserProvider currentUserProvider(AuthenticationManager authenticationManager) {
    return new DefaultCurrentUserProvider(authenticationManager);
  }

  @Bean
  @ConditionalOnMissingBean(PermissionExtend.class)
  PermissionExtend permissionExtend() {
    return new DefaultPermissionExtend();
  }

  @Bean
  @ConditionalOnMissingBean(CurrentUser.class)
  CurrentUser currentUser() {
    return new DefaultCurrentUser();
  }

  @Bean
  @ConditionalOnMissingBean(YakSecurityContextFilter.class)
  YakSecurityContextFilter yakSecurityContextFilter(
      ObjectProvider<UserRoleDao> userRoleDaoProvider,
      ObjectProvider<AuthenticationManager> authenticationManagerProvider,
      ObjectProvider<AuthorizationSnapshotService> authorizationSnapshotServiceProvider) {
    return new YakSecurityContextFilter(
        userRoleDaoProvider, authenticationManagerProvider, authorizationSnapshotServiceProvider);
  }

  @Bean
  @ConditionalOnMissingBean(OperationLogExtend.class)
  OperationLogExtend operationLogExtend() {
    return new NoOpOperationLogExtend();
  }

  @Bean
  @ConditionalOnMissingBean(ResourceExtend.class)
  ResourceExtend resourceExtend() {
    return new DefaultResourceExtendImpl();
  }

  @Bean
  @ConditionalOnMissingBean(NotificationPublisher.class)
  NotificationPublisher notificationPublisher(MessageService messageService) {
    return new DefaultNotificationPublisher(messageService);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "yak.security.permission-registration",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  PermissionRegistrationService permissionRegistrationService(PermissionDao permissionDao) {
    return new PermissionRegistrationService(permissionDao);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "yak.security.permission-registration",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  PermissionRegistrationInitializer permissionRegistrationInitializer(
      org.springframework.beans.factory.ListableBeanFactory beanFactory,
      PermissionRegistrationService registrationService) {
    return new PermissionRegistrationInitializer(beanFactory, registrationService);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "yak.security.bootstrap",
      name = "enabled",
      havingValue = "true")
  YakSecurityBootstrapInitializer securityBootstrapInitializer(
      YakSecurityProperties properties,
      UserService userService,
      RoleService roleService,
      RolePermissionService rolePermissionService,
      PermissionDao permissionDao) {
    return new YakSecurityBootstrapInitializer(
        properties, userService, roleService, rolePermissionService, permissionDao);
  }
}
