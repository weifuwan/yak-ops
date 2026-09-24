package io.yak.ops.security.common.entity.user;

import io.yak.ops.security.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 用户实体。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {
  /** 用户名。 */
  private String userName;
  /** 密码。 */
  @ToString.Exclude
  private String pw;
  /** 密码盐值。 */
  @ToString.Exclude
  private String salt;
  /** 真实姓名。 */
  private String realName;
  /** 手机号码。 */
  private String phone;
  /** 电子邮箱。 */
  private String email;
  /** 部门标识。 */
  private Long deptId;
  /** 用户状态：1 表示启用，2 表示禁用。 */
  private Integer status = 1;

}
