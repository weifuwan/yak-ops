package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.config.DataSourceConfig;
import io.yak.ops.dao.repository.security.impl.UserRepositoryImpl;
import io.yak.framework.security.service.impl.LoginServiceImpl;
import io.yak.framework.security.service.impl.UserAdministrationService;
import io.yak.framework.security.service.impl.UserServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** User/login database capability assembly. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "yak.security",
    name = "database-enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import({
    DataSourceConfig.class,
    UserRepositoryImpl.class,
    UserServiceImpl.class,
    UserAdministrationService.class,
    LoginServiceImpl.class,
    YakSecurityAuthenticationConfiguration.class
})
class YakSecurityDatabaseConfiguration {}
