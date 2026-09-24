package io.yak.ops.security.common.dto.user;

import lombok.Data;

import java.util.List;
/**
 * 用户数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class UserDTO {
  /** 用户名。 */
  private String userName;
  /** 密码。 */
  private String pw;
  /** 真实姓名。 */
  private String realName;
  /** 手机号码。 */
  private String phone;
  /** 电子邮箱。 */
  private String email;
  /** 角色标识列表。 */
  private List<Long> roleIds;

}
