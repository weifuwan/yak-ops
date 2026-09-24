package io.yak.ops.security.bootstrap;

import io.yak.framework.common.Result;
import io.yak.ops.security.common.dto.role.RoleSaveDTO;
import io.yak.ops.security.common.dto.user.UserDTO;
import io.yak.ops.security.common.entity.Permission;
import io.yak.ops.security.common.vo.role.RoleBriefVO;
import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.dao.PermissionDao;
import io.yak.ops.security.service.RoleService;
import io.yak.ops.security.service.RolePermissionService;
import io.yak.ops.security.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** Creates the first administrator for a newly installed application. */
public class YakSecurityBootstrapInitializer implements ApplicationRunner {
  private static final Logger LOGGER =
          LoggerFactory.getLogger(YakSecurityBootstrapInitializer.class);
  private static final String ADMINISTRATOR_ROLE = "系统管理员";
  private static final String BOOTSTRAP_OPERATOR = "yak-security-bootstrap";

  private final YakSecurityProperties properties;
  private final UserService userService;
  private final RoleService roleService;
  private final RolePermissionService rolePermissionService;
  private final PermissionDao permissionDao;

  public YakSecurityBootstrapInitializer(
          YakSecurityProperties properties,
          UserService userService,
          RoleService roleService,
          RolePermissionService rolePermissionService,
          PermissionDao permissionDao) {
    this.properties = properties;
    this.userService = userService;
    this.roleService = roleService;
    this.rolePermissionService = rolePermissionService;
    this.permissionDao = permissionDao;
  }

  @Override
  @Transactional(transactionManager = "yakSecurityTransactionManager", rollbackFor = Exception.class)
  public void run(ApplicationArguments args) {
    if (!userService.getAllUserBriefList().isEmpty()) {
      return;
    }

    YakSecurityProperties.BootstrapProperties bootstrap = properties.getBootstrap();
    requireText(bootstrap.getUsername(), "username");
    requireText(bootstrap.getPassword(), "password");
    requireText(bootstrap.getRealName(), "real-name");

    List<Long> permissionIds = permissionDao.selectAllAndAscOrderByLevel().stream()
            .map(Permission::getId)
            .toList();
    if (permissionIds.isEmpty()) {
      throw new IllegalStateException("Cannot bootstrap Yak Security: no built-in permissions exist");
    }

    RoleBriefVO administratorRole = roleService
            .getRoleBriefListByRoleName(ADMINISTRATOR_ROLE).stream()
            .findFirst()
            .orElseGet(() -> createAdministratorRole(permissionIds));
    rolePermissionService.updateRolePermission(
            administratorRole.getId(), permissionIds);

    UserDTO administrator = new UserDTO();
    administrator.setUserName(bootstrap.getUsername());
    administrator.setPw(bootstrap.getPassword());
    administrator.setRealName(bootstrap.getRealName());
    administrator.setRoleIds(List.of(administratorRole.getId()));
    Result<Void> result = userService.addUser(administrator, BOOTSTRAP_OPERATOR);
    if (result.failed()) {
      throw new IllegalStateException(
              "Cannot bootstrap Yak Security administrator: " + result.getMessage());
    }

    LOGGER.warn("Yak Security bootstrap administrator '{}' was created. "
            + "Disable yak.security.bootstrap.enabled now.", bootstrap.getUsername());
  }

  private RoleBriefVO createAdministratorRole(List<Long> permissionIds) {
    RoleSaveDTO role = new RoleSaveDTO();
    role.setRoleName(ADMINISTRATOR_ROLE);
    role.setDescription("Yak Security bootstrap administrator role");
    role.setPermissionIdList(permissionIds);
    roleService.createRole(role, BOOTSTRAP_OPERATOR);
    return roleService.getRoleBriefListByRoleName(ADMINISTRATOR_ROLE).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "Cannot bootstrap Yak Security: administrator role was not created"));
  }

  private static void requireText(String value, String property) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(
              "yak.security.bootstrap." + property + " must be configured when bootstrap is enabled");
    }
  }
}
