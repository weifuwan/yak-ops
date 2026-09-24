package io.yak.ops.security.common.vo.role;

import lombok.Data;
/**
 * 角色简要信息视图对象。
 *
 * @author weifuwan
 */
@Data
public class RoleBriefVO {
  /** 主键标识。 */
  private Long id;
  /** 角色名称。 */
  private String roleName;

}
