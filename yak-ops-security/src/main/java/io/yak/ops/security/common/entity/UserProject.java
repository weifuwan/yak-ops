package io.yak.ops.security.common.entity;

import lombok.Data;

/**
 * 用户与项目关联实体。
 *
 * @author weifuwan
 */
@Data
public class UserProject {
  /** 用户标识。 */
  private Long userId;
  /** 用户类型。 */
  private Integer userType;
  /** 项目标识。 */
  private Long projectId;

}
