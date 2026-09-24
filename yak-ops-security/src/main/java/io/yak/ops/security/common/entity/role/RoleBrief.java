package io.yak.ops.security.common.entity.role;

import lombok.Data;

/**
 * 角色简要信息实体。
 *
 * @author weifuwan
 */
@Data
public class RoleBrief {
  /** 实体标识。 */
  private Long id;
  /** 角色名称。 */
  private String roleName;

}
