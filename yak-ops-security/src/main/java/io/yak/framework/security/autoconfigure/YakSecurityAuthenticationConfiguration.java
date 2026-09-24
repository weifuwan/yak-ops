package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.authentication.HttpSessionAuthenticationManager;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.extend.LoginExtend;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.extend.impl.DefaultCurrentUserProvider;
import io.yak.framework.security.extend.impl.DefaultLoginExtendImpl;
import io.yak.framework.security.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Yak Security 登录态配置。 */
@Configuration(proxyBeanMethods = false)
class YakSecurityAuthenticationConfiguration {

  @Bean
  @ConditionalOnMissingBean(AuthenticationManager.class)
  AuthenticationManager authenticationManager(
          YakSecurityProperties properties) {
    return new HttpSessionAuthenticationManager(
            properties.getAuthentication()
                    .getIdleTimeout());
  }

  @Bean
  @ConditionalOnMissingBean(LoginExtend.class)
  LoginExtend loginExtend(
          UserService userService,
          PasswordEncoder passwordEncoder,
          YakSecurityProperties properties,
          AuthenticationManager authenticationManager) {
    return new DefaultLoginExtendImpl(
            userService,
            passwordEncoder,
            properties,
            authenticationManager);
  }

  @Bean
  @ConditionalOnMissingBean(CurrentUserProvider.class)
  CurrentUserProvider currentUserProvider(
          AuthenticationManager authenticationManager) {
    return new DefaultCurrentUserProvider(
            authenticationManager);
  }
}
