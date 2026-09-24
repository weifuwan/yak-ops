package io.yak.ops.security.common.entity.role;

import io.yak.ops.security.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Role extends BaseEntity {
  /** 角色编码。 */
  private String roleCode;
  /** 角色名称。 */
  private String roleName;
  /** 描述。 */
  private String description;
  /** 最后修改人。 */
  private String lastReviser;

}
