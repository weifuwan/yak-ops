package io.yak.ops.security.common.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户与角色关联实体。
 *
 * @author weifuwan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

  /** 用户标识。 */
  private Long userId;

  /** 角色标识。 */
  private Long roleId;
}