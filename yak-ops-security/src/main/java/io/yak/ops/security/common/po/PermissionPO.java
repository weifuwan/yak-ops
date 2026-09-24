package io.yak.ops.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 权限持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_permission")
public class PermissionPO extends BasePO {

  /** 权限编码。 */
  private String permissionCode;

  /** 权限名称。 */
  private String permissionName;

  /** 上级权限标识。 */
  private Long parentId;

  /** 是否为叶子权限。 */
  private Boolean leaf;

  /** 权限层级。 */
  private Integer level;

  /** 权限描述。 */
  private String description;

  /** 所属稳定菜单编码；按钮权限通过该字段自动包含菜单访问能力。 */
  private String menuCode;

  private Boolean active;

  private Boolean declared;
}
