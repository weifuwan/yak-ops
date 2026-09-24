package io.yak.framework.security.autoconfigure;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisx;
import cn.dev33.satoken.stp.StpUtil;
import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.authentication.SaTokenAuthenticationManager;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.extend.LoginExtend;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.extend.impl.DefaultCurrentUserProvider;
import io.yak.framework.security.extend.impl.DefaultLoginExtendImpl;
import io.yak.framework.security.service.UserService;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** Yak Security 唯一登录态后端：Sa-Token。 */
@Configuration(proxyBeanMethods = false)
class YakSecurityAuthenticationConfiguration {

  private static final String AUTHENTICATION_PREFIX = "yak.security.authentication";

  @Bean
  @ConditionalOnMissingBean(SaTokenDao.class)
  @ConditionalOnProperty(
          prefix = AUTHENTICATION_PREFIX,
          name = "storage",
          havingValue = "redis")
  SaTokenDao saTokenRedisDao(YakSecurityProperties properties) {
    YakSecurityProperties.RedisStorageProperties redis =
            properties.getAuthentication().getRedis();
    validateRedis(redis);

    Properties redisProperties = new Properties();
    redisProperties.setProperty("server", redis.getHost() + ":" + redis.getPort());
    redisProperties.setProperty("db", Integer.toString(redis.getDatabase()));
    redisProperties.setProperty("maxTotal", Integer.toString(redis.getMaxTotal()));
    if (StringUtils.hasText(redis.getPassword())) {
      redisProperties.setProperty("password", redis.getPassword());
    }
    return new SaTokenDaoForRedisx(redisProperties);
  }

  @Bean
  @ConditionalOnMissingBean(AuthenticationManager.class)
  AuthenticationManager authenticationManager(YakSecurityProperties properties) {
    return new SaTokenAuthenticationManager(
            StpUtil.getStpLogic(),
            properties.getAuthentication().getIdleTimeout());
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
  CurrentUserProvider currentUserProvider(AuthenticationManager authenticationManager) {
    return new DefaultCurrentUserProvider(authenticationManager);
  }

  private static void validateRedis(
          YakSecurityProperties.RedisStorageProperties redis) {
    if (!StringUtils.hasText(redis.getHost())) {
      throw new IllegalStateException(
              "Invalid configuration: yak.security.authentication.redis.host must not be blank");
    }
    if (redis.getPort() < 1 || redis.getPort() > 65_535) {
      throw new IllegalStateException(
              "Invalid configuration: yak.security.authentication.redis.port must be between 1 and 65535");
    }
    if (redis.getDatabase() < 0) {
      throw new IllegalStateException(
              "Invalid configuration: yak.security.authentication.redis.database must be greater than or equal to 0");
    }
    if (redis.getMaxTotal() < 1) {
      throw new IllegalStateException(
              "Invalid configuration: yak.security.authentication.redis.max-total must be greater than 0");
    }
  }
}
