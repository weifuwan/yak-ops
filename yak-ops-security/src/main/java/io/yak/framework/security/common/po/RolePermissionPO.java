package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 角色权限关联持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_role_permission")
public class RolePermissionPO extends BasePO {

  /**
   * 角色标识。
   */
  private Long roleId;

  /**
   * 权限标识。
   */
  private Long permissionId;
}