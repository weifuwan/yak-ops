package io.yak.framework.security.common.dto.user;

import lombok.Data;

/**
 * 用户项目关系数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class UserProjectDTO {
  /** 标识。 */
  private Long id;
  /** 用户标识。 */
  private Long userId;
  /** 用户类型。 */
  private Integer userType;
  /** 项目标识。 */
  private Long projectId;
  /** 是否删除。 */
  private Boolean isDelete;

}
