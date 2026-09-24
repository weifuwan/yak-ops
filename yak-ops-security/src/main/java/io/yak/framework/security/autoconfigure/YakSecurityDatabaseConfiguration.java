package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.config.DataSourceConfig;
import io.yak.framework.security.dao.impl.*;
import io.yak.framework.security.notification.DefaultNotificationPublisher;
import io.yak.framework.security.service.impl.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "yak.security", name = "database-enabled", havingValue = "true", matchIfMissing = true)
@Import({DataSourceConfig.class, ConfigDaoImpl.class, DeptDaoImpl.class,
    MessageDaoImpl.class, OplogDaoImpl.class, OplogExtraDaoImpl.class,
    PermissionDaoImpl.class, ProjectDaoImpl.class, ResourceTypeDaoImpl.class,
    RoleDaoImpl.class, RolePermissionDaoImpl.class, UserDaoImpl.class,
    UserProjectDaoImpl.class, UserResourceDaoImpl.class, UserRoleDaoImpl.class,
    CaffeinePermissionCache.class, ConfigServiceImpl.class, DeptServiceImpl.class,
    LoginServiceImpl.class, MenuAuthorizationService.class,
    PermissionMenuRelationService.class,
    MenuAwarePermissionService.class, MenuAwareRolePermissionService.class,
    UserMenuGrantService.class, CurrentUserProjectResolver.class,
    MessageServiceImpl.class, DefaultNotificationPublisher.class,
    OplogExtraServiceImpl.class, OplogServiceImpl.class,
    PermissionAdministrationService.class, PermissionServiceImpl.class,
    ProjectServiceImpl.class, AuthorizationSnapshotService.class,
    RbacPermissionServiceImpl.class, ResourceTypeServiceImpl.class,
    RolePermissionServiceImpl.class, RoleServiceImpl.class, UserProjectServiceImpl.class,
    UserAdministrationService.class, UserResourceServiceImpl.class,
    UserRoleServiceImpl.class, UserServiceImpl.class,
    YakSecurityAuthenticationConfiguration.class})
class YakSecurityDatabaseConfiguration {}
