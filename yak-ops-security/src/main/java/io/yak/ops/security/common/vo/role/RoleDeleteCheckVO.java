package io.yak.ops.security.common.vo.role;

import lombok.Data;

import java.util.List;
/**
 * 角色删除检查结果视图对象。
 *
 * @author weifuwan
 */
@Data
public class RoleDeleteCheckVO {
  /** 角色标识。 */
  private Long roleId;
  /** 关联的用户名列表。 */
  private List<String> userNameList;

}
