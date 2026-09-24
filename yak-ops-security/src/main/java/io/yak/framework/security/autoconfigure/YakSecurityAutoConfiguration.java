package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.bootstrap.YakSecurityBootstrapInitializer;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.extend.impl.DefaultPasswordEncoder;
import io.yak.framework.security.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Yak Security user/login auto configuration. */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YakSecurityProperties.class)
@ConditionalOnProperty(
    prefix = "yak.security",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import({
    YakSecurityAutoConfiguration.ExtensionConfiguration.class,
    YakSecurityDatabaseConfiguration.class,
    YakSecurityWebConfiguration.class,
    YakSecurityOpenApiConfiguration.class
})
public class YakSecurityAutoConfiguration {

  @Bean
  @ConditionalOnBean(UserService.class)
  @ConditionalOnProperty(
      prefix = "yak.security.bootstrap",
      name = "enabled",
      havingValue = "true")
  YakSecurityBootstrapInitializer yakSecurityBootstrapInitializer(
      YakSecurityProperties properties,
      UserService userService) {
    return new YakSecurityBootstrapInitializer(properties, userService);
  }

  static class ExtensionConfiguration {
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder passwordEncoder() {
      return new DefaultPasswordEncoder();
    }
  }
}
