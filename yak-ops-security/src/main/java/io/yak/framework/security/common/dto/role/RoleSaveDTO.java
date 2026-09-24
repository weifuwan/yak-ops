package io.yak.framework.security.common.dto.role;

import lombok.Data;

import java.util.List;
/**
 * 角色保存数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class RoleSaveDTO {
  /** 标识。 */
  private Long id;
  /** 角色名称。 */
  private String roleName;
  /** 描述。 */
  private String description;
  /** 权限标识列表。 */
  private List<Long> permissionIdList;

}
