package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户角色关联持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_user_role")
public class UserRolePO extends BasePO {

  /**
   * 用户标识。
   */
  private Long userId;

  /**
   * 角色标识。
   */
  private Long roleId;
}