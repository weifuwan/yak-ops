package io.yak.ops.security.common.entity.user;

import lombok.Data;

/**
 * 用户简要信息实体。
 *
 * @author weifuwan
 */
@Data
public class UserBrief {
  /** 实体标识。 */
  private Long id;
  /** 用户名。 */
  private String userName;
  /** 真实姓名。 */
  private String realName;
  /** 部门标识。 */
  private Long deptId;

}
