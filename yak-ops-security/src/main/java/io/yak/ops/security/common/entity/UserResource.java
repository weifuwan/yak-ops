package io.yak.ops.security.common.entity;

import lombok.Data;

/**
 * 用户资源关联实体。
 *
 * @author weifuwan
 */
@Data
public class UserResource {

  /**
   * 用户标识。
   */
  private Long userId;

  /**
   * 项目标识。
   */
  private Long projectId;

  /**
   * 资源类型标识。
   */
  private Long resourceTypeId;

  /**
   * 资源标识。
   */
  private Long resourceId;

  /**
   * 控制级别。
   */
  private Integer controlLevel;
}