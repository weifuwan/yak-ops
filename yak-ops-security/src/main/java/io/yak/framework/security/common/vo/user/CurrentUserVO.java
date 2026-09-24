package io.yak.framework.security.common.vo.user;

import lombok.Data;

/** 当前登录用户身份。 */
@Data
public class CurrentUserVO {
  private Long id;
  private String userName;
  private String realName;
  private Long deptId;
  private String phone;
  private String email;
}
