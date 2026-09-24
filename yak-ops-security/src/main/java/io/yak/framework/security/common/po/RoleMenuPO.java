package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 角色菜单关联持久化对象。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_role_menu")
public class RoleMenuPO extends BasePO {

  /** 角色标识。 */
  private Long roleId;

  /** 菜单标识。 */
  private Long menuId;
}
