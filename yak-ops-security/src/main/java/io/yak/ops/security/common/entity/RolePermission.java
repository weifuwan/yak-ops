package io.yak.ops.security.common.entity;

import lombok.Data;

/**
 * 角色与权限关联实体。
 *
 * @author weifuwan
 */
@Data
public class RolePermission {
  /** 角色标识。 */
  private Long roleId;
  /** 权限标识。 */
  private Long permissionId;

}
