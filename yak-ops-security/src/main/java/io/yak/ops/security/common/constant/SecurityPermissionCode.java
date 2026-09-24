package io.yak.ops.security.common.constant;

/**
 * Yak Security 内置管理接口使用的稳定权限编码。
 *
 * <p>宿主应用和前端应引用相同的字符串约定，避免按钮权限与接口权限发生漂移。
 */
public final class SecurityPermissionCode {

  public static final String GROUP_CODE = "security";
  public static final String GROUP_NAME = "系统管理";
  public static final String ROOT = "security:root";

  private SecurityPermissionCode() {
  }

  /** 用户管理权限。 */
  public static final class User {
    public static final String READ = "security:user:read";
    public static final String CREATE = "security:user:create";
    public static final String UPDATE = "security:user:update";
    public static final String RESET_PASSWORD = "security:user:reset-password";
    public static final String DELETE = "security:user:delete";

    private User() {
    }
  }

  /** 角色管理权限。 */
  public static final class Role {
    public static final String READ = "security:role:read";
    public static final String CREATE = "security:role:create";
    public static final String UPDATE = "security:role:update";
    public static final String ASSIGN = "security:role:assign";
    public static final String DELETE = "security:role:delete";

    private Role() {
    }
  }

  /** 权限目录管理权限。 */
  public static final class Permission {
    public static final String MENU_CODE = "system-permissions";
    public static final String READ = "security:permission:read";
    public static final String IMPORT = "security:permission:import";
    public static final String DELETE = "security:permission:delete";

    private Permission() {
    }
  }

  /** 部门管理权限。 */
  public static final class Department {
    public static final String MENU_CODE = "system-departments";
    public static final String READ = "security:department:read";
    public static final String CREATE = "security:department:create";
    public static final String EDIT = "security:department:edit";
    public static final String DELETE = "security:department:delete";
    public static final String IMPORT = "security:department:import";

    private Department() {
    }
  }

  /** 安全项目管理权限。 */
  public static final class Project {
    public static final String MENU_CODE = "system-security-projects";
    public static final String READ = "security:project:read";

    private Project() {
    }
  }

  /** 资源授权管理权限。 */
  public static final class ResourcePermission {
    public static final String READ = "security:resource-permission:read";

    private ResourcePermission() {
    }
  }

  /** 系统配置管理权限。 */
  public static final class Config {
    public static final String READ = "security:config:read";

    private Config() {
    }
  }

  /** 操作日志查询权限。 */
  public static final class OperationLog {
    public static final String READ = "security:operation-log:read";

    private OperationLog() {
    }
  }
}
