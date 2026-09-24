package io.yak.ops.security.common.vo.user;

import lombok.Data;
/**
 * 用户基础信息视图对象。
 *
 * @author weifuwan
 */
@Data
public class UserBasicVO {
  /** 主键标识。 */
  private Long id;
  /** 用户名。 */
  private String userName;
  /** 用户真实姓名。 */
  private String realName;
  /** 部门标识。 */
  private Long deptId;

}
