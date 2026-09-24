package io.yak.ops.security.common.dto.role;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.yak.ops.security.common.dto.PageParamDTO;
/**
 * 角色查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQueryDTO extends PageParamDTO {
  /** 角色编码。 */
  private String roleCode;
  /** 标识。 */
  private Long id;
  /** 角色名称。 */
  private String roleName;
  /** 描述。 */
  private String description;

}
